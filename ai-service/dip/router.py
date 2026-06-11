import io
from typing import Optional

import cv2
import httpx
import numpy as np
from fastapi import APIRouter, Depends, File, HTTPException, Request, UploadFile
from fastapi.responses import JSONResponse
from pydantic import BaseModel

from app.config import settings
from app.middleware.auth import verify_token
from app.utils.safety_limits import enforce_ai_concurrency, enforce_ai_rate_limit
from dip.detect import detect_faces, crop_faces
from dip.preprocess import batch as preprocess_batch
from dip.features import EigenfaceExtractor
from dip.cluster import cluster

router = APIRouter(dependencies=[
    Depends(verify_token),
    Depends(enforce_ai_rate_limit),
    Depends(enforce_ai_concurrency),
])

_N_COMPONENTS = 50


def _decode(file_bytes: bytes) -> np.ndarray:
    arr = np.frombuffer(file_bytes, np.uint8)
    img = cv2.imdecode(arr, cv2.IMREAD_GRAYSCALE)
    if img is None:
        raise ValueError("Cannot decode image")
    return img


@router.post("/faces/cluster")
async def cluster_faces(files: list[UploadFile] = File(...)) -> JSONResponse:
    """
    Accept multiple images, detect faces, cluster by identity.
    Returns groups: each group is a list of (file_index, face_index, bbox).
    """
    all_faces: list[np.ndarray] = []
    face_meta: list[dict] = []

    for file_idx, upload in enumerate(files):
        raw = await upload.read()
        try:
            img = _decode(raw)
        except ValueError:
            raise HTTPException(status_code=400, detail=f"File {upload.filename} is not a valid image.")
        boxes = detect_faces(img)
        crops = crop_faces(img, boxes)
        # If no face detected, treat the whole image as a face crop
        if not crops:
            crops = [img]
            boxes = [(0, 0, img.shape[1], img.shape[0])]
        for face_idx, (crop, box) in enumerate(zip(crops, boxes)):
            all_faces.append(crop)
            face_meta.append({
                "file_index": file_idx,
                "filename": upload.filename,
                "face_index": face_idx,
                "bbox": {"x": int(box[0]), "y": int(box[1]), "w": int(box[2]), "h": int(box[3])},
            })

    if len(all_faces) < 2:
        return JSONResponse(content={
            "success": True,
            "groups": [],
            "total_faces": len(all_faces),
            "silhouette_score": None,
        })

    X = preprocess_batch(all_faces)
    k = min(_N_COMPONENTS, X.shape[0] - 1)
    features = EigenfaceExtractor(n_components=k).fit(X).transform(X)
    labels, score = cluster(features)

    groups: dict[int, list] = {}
    for meta, label in zip(face_meta, labels.tolist()):
        groups.setdefault(label, []).append(meta)

    return JSONResponse(content={
        "success": True,
        "groups": [{"group_id": gid, "faces": faces} for gid, faces in sorted(groups.items())],
        "total_faces": len(all_faces),
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
    """
    Accept pre-uploaded image URLs, download and cluster faces.
    photo_ids must correspond 1:1 with urls.
    """
    if len(req.urls) != len(req.photo_ids):
        raise HTTPException(status_code=400, detail="urls and photo_ids must have the same length")

    all_faces: list[np.ndarray] = []
    face_meta: list[dict] = []
    authorization: Optional[str] = request.headers.get("Authorization")
    request_headers = {"Authorization": authorization} if authorization else {}

    async with httpx.AsyncClient(timeout=30, follow_redirects=True) as client:
        for file_idx, (url, photo_id) in enumerate(zip(req.urls, req.photo_ids)):
            resolved_url = _resolve_photo_url(url)
            try:
                resp = await client.get(resolved_url, headers=request_headers)
                resp.raise_for_status()
            except Exception as e:
                raise HTTPException(status_code=400, detail=f"Failed to fetch {resolved_url}: {e}")
            try:
                img = _decode(resp.content)
            except ValueError:
                raise HTTPException(status_code=400, detail=f"Cannot decode image at {resolved_url}")
            boxes = detect_faces(img)
            crops = crop_faces(img, boxes)
            if not crops:
                crops = [img]
                boxes = [(0, 0, img.shape[1], img.shape[0])]
            for face_idx, (crop, box) in enumerate(zip(crops, boxes)):
                all_faces.append(crop)
                face_meta.append({
                    "photo_id": photo_id,
                    "file_index": file_idx,
                    "face_index": face_idx,
                    "bbox": {"x": int(box[0]), "y": int(box[1]), "w": int(box[2]), "h": int(box[3])},
                })

    if len(all_faces) < 2:
        return JSONResponse(content={"success": True, "groups": [], "total_faces": len(all_faces), "silhouette_score": None})

    X = preprocess_batch(all_faces)
    k = min(_N_COMPONENTS, X.shape[0] - 1)
    features = EigenfaceExtractor(n_components=k).fit(X).transform(X)
    labels, score = cluster(features)

    groups: dict[int, list] = {}
    for meta, label in zip(face_meta, labels.tolist()):
        groups.setdefault(label, []).append(meta)

    return JSONResponse(content={
        "success": True,
        "groups": [{"group_id": gid, "faces": faces} for gid, faces in sorted(groups.items())],
        "total_faces": len(all_faces),
        "silhouette_score": score,
    })
