import cv2
import numpy as np

TARGET_SIZE = (64, 64)

# Eye landmark indices from dlib/mediapipe are not available here;
# AT&T images are already frontal, so we skip geometric alignment
# and rely on equalization + resize for normalization.


def pipeline(face_img: np.ndarray) -> tuple[np.ndarray, dict[str, np.ndarray]]:
    """
    Returns (processed, steps) where steps holds intermediate images for visualization.
    Input may be grayscale or BGR.
    """
    steps: dict[str, np.ndarray] = {}

    gray = face_img if face_img.ndim == 2 else cv2.cvtColor(face_img, cv2.COLOR_BGR2GRAY)
    steps["gray"] = gray.copy()

    equalized = cv2.equalizeHist(gray)
    steps["equalized"] = equalized.copy()

    resized = cv2.resize(equalized, TARGET_SIZE, interpolation=cv2.INTER_AREA)
    steps["resized"] = resized.copy()

    normalized = resized.astype(np.float32) / 255.0
    steps["normalized"] = (normalized * 255).astype(np.uint8)

    return normalized, steps


def batch(face_imgs: list[np.ndarray]) -> np.ndarray:
    """Apply pipeline to a list of face crops; return (N, H*W) float32 matrix."""
    processed = [pipeline(img)[0].flatten() for img in face_imgs]
    return np.array(processed, dtype=np.float32)
