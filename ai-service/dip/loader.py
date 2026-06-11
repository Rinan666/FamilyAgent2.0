import os
import numpy as np
import cv2


def load_att(data_dir: str) -> tuple[np.ndarray, np.ndarray]:
    """Load AT&T (ORL) dataset. Expects subdirs s1..s40, each with 10 PGM files."""
    images, labels = [], []
    for subject_id in range(1, 41):
        subject_dir = os.path.join(data_dir, f"s{subject_id}")
        if not os.path.isdir(subject_dir):
            continue
        for fname in sorted(os.listdir(subject_dir)):
            if not fname.endswith(".pgm"):
                continue
            img = cv2.imread(os.path.join(subject_dir, fname), cv2.IMREAD_GRAYSCALE)
            if img is not None:
                images.append(img)
                labels.append(subject_id)
    return np.array(images), np.array(labels)


def load_yale(data_dir: str) -> tuple[np.ndarray, np.ndarray]:
    """Load Yale Face Database.
    Supports both original (subject01.normal) and synthetic (subject01_normal.png) naming.
    """
    images, labels = [], []
    for fname in sorted(os.listdir(data_dir)):
        path = os.path.join(data_dir, fname)
        if not os.path.isfile(path):
            continue
        base = fname.split(".")[0]  # works for both "subject01" and "subject01_normal"
        if not base.startswith("subject"):
            continue
        label = int(base[len("subject"):].split("_")[0])
        img = cv2.imread(path, cv2.IMREAD_GRAYSCALE)
        if img is not None:
            images.append(img)
            labels.append(label)
    return np.array(images), np.array(labels)
