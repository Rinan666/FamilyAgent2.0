import cv2
import numpy as np

_CASCADE_PATH = cv2.data.haarcascades + "haarcascade_frontalface_default.xml"
_cascade = cv2.CascadeClassifier(_CASCADE_PATH)


def detect_faces(
    img: np.ndarray,
    scale_factor: float = 1.1,
    min_neighbors: int = 5,
    min_size: tuple[int, int] = (30, 30),
) -> list[tuple[int, int, int, int]]:
    """Return list of (x, y, w, h) bounding boxes."""
    gray = img if img.ndim == 2 else cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    faces = _cascade.detectMultiScale(
        gray, scaleFactor=scale_factor, minNeighbors=min_neighbors, minSize=min_size
    )
    return [tuple(f) for f in faces] if len(faces) else []


def crop_faces(img: np.ndarray, boxes: list[tuple]) -> list[np.ndarray]:
    gray = img if img.ndim == 2 else cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    return [gray[y : y + h, x : x + w] for (x, y, w, h) in boxes]
