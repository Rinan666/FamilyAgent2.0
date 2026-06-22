import asyncio
import logging
from typing import Optional

import cv2
import httpx
import numpy as np
from fastapi import APIRouter, Depends, File, HTTPException, Request, UploadFile
from fastapi.responses import JSONResponse
from insightface.app import FaceAnalysis
from pydantic import BaseModel

from app.config import settings
from app.middleware.auth import verify_token
from app.utils.safety_limits import enforce_ai_concurrency, enforce_ai_rate_limit
from dip.cluster import cluster

router = APIRouter(dependencies=[
    Depends(verify_token),
    Depends(enforce_ai_rate_limit),
    Depends(enforce_ai_concurrency),
])
logger = logging.getLogger(__name__)

_face_app = FaceAnalysis(name="buffalo_sc", providers=["CPUExecutionProvider"])
_face_app.prepare(ctx_id=0, det_size=(640, 640))


def _decode_bgr(data: bytes) -> np.ndarray:
    arr = np.frombuffer(data, np.uint8)
    img = cv2.imdecode(arr, cv2.IMREAD_COLOR)
    if img is None:
        raise ValueError("Cannot decode image")
    return img


def _ensure_min_size(img: np.ndarray, min_side: int = 160) -> np.ndarray:
    h, w = img.shape[:2]
    if min(h, w) < min_side:
        scale = min_side / min(h, w)
        img = cv2.resize(img, (int(w * scale), int(h * scale)), interpolation=cv2.INTER_CUBIC)
    return img


def _extract(img_bgr: np.ndarray) -> list[tuple[np.ndarray, tuple]]:
    """Return [(embedding_512, (x,y,w,h)), ...]. Falls back to whole-image if no face detected."""
    img_bgr = _ensure_min_size(img_bgr)
    faces = _face_app.get(img_bgr)
    if faces:
        return [
            (face.embedding.astype(np.float32),
             tuple(int(v) for v in [face.bbox[0], face.bbox[1],
                                    face.bbox[2] - face.bbox[0],
                                    face.bbox[3] - face.bbox[1]]))
            for face in faces
        ]
    h, w = img_bgr.shape[:2]
    return [(np.zeros(512, dtype=np.float32), (0, 0, w, h))]


@router.post("/faces/cluster")
async def cluster_faces(files: list[UploadFile] = File(...)) -> JSONResponse:
    all_embeddings: list[np.ndarray] = []
    face_meta: list[dict] = []

    for file_idx, upload in enumerate(files):
        raw = await upload.read()
        try:
            img = _decode_bgr(raw)
        except ValueError:
            raise HTTPException(status_code=400, detail=f"{upload.filename} is not a valid image.")
        for face_idx, (emb, box) in enumerate(_extract(img)):
            all_embeddings.append(emb)
            face_meta.append({
                "file_index": file_idx,
                "filename": upload.filename,
                "face_index": face_idx,
                "bbox": {"x": box[0], "y": box[1], "w": box[2], "h": box[3]},
            })

    if len(all_embeddings) < 2:
        return JSONResponse(content={
            "success": True, "groups": [], "total_faces": len(all_embeddings), "silhouette_score": None,
        })

    labels, score = cluster(np.array(all_embeddings), eps=0.5)
    groups: dict[int, list] = {}
    for meta, label in zip(face_meta, labels.tolist()):
        groups.setdefault(label, []).append(meta)

    return JSONResponse(content={
        "success": True,
        "groups": [{"group_id": gid, "faces": faces} for gid, faces in sorted(groups.items())],
        "total_faces": len(all_embeddings),
        "silhouette_score": score,
    })


class ClusterByUrlsRequest(BaseModel):
    urls: list[str]
    photo_ids: list[int]


def _resolve_photo_url(url: str) -> str:
    if url.startswith("/"):
        return f"{settings.backend_url.rstrip('/')}{url}"
    return url


@router.post("/faces/cluster-by-urls")
async def cluster_faces_by_urls(req: ClusterByUrlsRequest, request: Request) -> JSONResponse:
    if len(req.urls) != len(req.photo_ids):
        raise HTTPException(status_code=400, detail="urls and photo_ids must have the same length")

    authorization: Optional[str] = request.headers.get("Authorization")
    request_headers = {"Authorization": authorization} if authorization else {}

    async def _fetch(file_idx: int, url: str, photo_id: int):
        resolved_url = _resolve_photo_url(url)
        try:
            resp = await client.get(resolved_url, headers=request_headers)
            resp.raise_for_status()
        except httpx.HTTPStatusError as e:
            status_code = e.response.status_code if e.response is not None else None
            logger.warning("Skipping photo %s during clustering: fetch returned %s", photo_id, status_code)
            return {
                "faces": [],
                "failure": {
                    "photo_id": photo_id,
                    "file_index": file_idx,
                    "reason": "HTTP_STATUS",
                    "status_code": status_code,
                },
            }
        except httpx.HTTPError as e:
            logger.warning("Skipping photo %s during clustering: %s", photo_id, type(e).__name__)
            return {
                "faces": [],
                "failure": {
                    "photo_id": photo_id,
                    "file_index": file_idx,
                    "reason": type(e).__name__,
                    "status_code": None,
                },
            }
        try:
            img = _decode_bgr(resp.content)
        except ValueError:
            logger.warning("Skipping photo %s during clustering: cannot decode image", photo_id)
            return {
                "faces": [],
                "failure": {
                    "photo_id": photo_id,
                    "file_index": file_idx,
                    "reason": "DECODE_FAILED",
                    "status_code": None,
                },
            }
        return {
            "faces": [
                (emb, {"photo_id": photo_id, "file_index": file_idx, "face_index": fi,
                       "bbox": {"x": box[0], "y": box[1], "w": box[2], "h": box[3]}})
                for fi, (emb, box) in enumerate(_extract(img))
            ],
            "failure": None,
        }

    async with httpx.AsyncClient(timeout=120, follow_redirects=True) as client:
        fetched = await asyncio.gather(*[
            _fetch(i, url, pid) for i, (url, pid) in enumerate(zip(req.urls, req.photo_ids))
        ])

    all_embeddings: list[np.ndarray] = []
    face_meta: list[dict] = []
    failed_photos: list[dict] = []
    for file_results in fetched:
        if file_results["failure"] is not None:
            failed_photos.append(file_results["failure"])
        for emb, meta in file_results["faces"]:
            all_embeddings.append(emb)
            face_meta.append(meta)

    if len(all_embeddings) < 2:
        return JSONResponse(content={
            "success": True,
            "groups": [],
            "total_faces": len(all_embeddings),
            "silhouette_score": None,
            "failed_photos": failed_photos,
        })

    labels, score = cluster(np.array(all_embeddings), eps=0.5)
    groups: dict[int, list] = {}
    for meta, label in zip(face_meta, labels.tolist()):
        groups.setdefault(label, []).append(meta)

    return JSONResponse(content={
        "success": True,
        "groups": [{"group_id": gid, "faces": faces} for gid, faces in sorted(groups.items())],
        "total_faces": len(all_embeddings),
        "silhouette_score": score,
        "failed_photos": failed_photos,
    })
