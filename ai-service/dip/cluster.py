import numpy as np
from sklearn.cluster import DBSCAN
from sklearn.metrics import silhouette_score
from sklearn.neighbors import NearestNeighbors
from sklearn.preprocessing import normalize


def _auto_eps(X: np.ndarray, k: int = 3, metric: str = "cosine", percentile: float = 10.0) -> float:
    """Estimate eps as a low percentile of k-NN distances.
    For small datasets, clamp k to ~n/5 so the kth neighbor stays within
    the expected cluster, avoiding cross-cluster contamination in eps estimation.
    """
    n = len(X)
    k = min(k, max(1, n // 5), n - 1)
    nbrs = NearestNeighbors(n_neighbors=k, metric=metric).fit(X)
    dists, _ = nbrs.kneighbors(X)
    kth_dists = np.sort(dists[:, -1])
    val = float(max(np.percentile(kth_dists, percentile), 1e-6))
    print(f"[DIP] auto_eps={val:.6f} k={k} n={n}", flush=True)
    return val


def cluster(
    features: np.ndarray,
    eps: float | None = None,
    min_samples: int = 2,
    metric: str = "cosine",
) -> tuple[np.ndarray, float | None]:
    """
    Returns (labels, silhouette_score).
    Labels == -1 are noise points.
    eps=None triggers automatic estimation via k-NN elbow method.
    silhouette_score is None when fewer than 2 clusters are found.
    """
    X = normalize(features) if metric == "cosine" else features
    if eps is None:
        eps = _auto_eps(X, metric=metric)
    labels = DBSCAN(eps=eps, min_samples=min_samples, metric=metric).fit_predict(X)
    n_clusters = len(set(labels) - {-1})
    score = None
    if n_clusters >= 2 and (labels != -1).sum() >= 2:
        mask = labels != -1
        score = float(silhouette_score(X[mask], labels[mask], metric=metric))
    return labels, score
