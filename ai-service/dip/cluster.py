import numpy as np
from sklearn.cluster import DBSCAN
from sklearn.metrics import silhouette_score
from sklearn.neighbors import NearestNeighbors
from sklearn.preprocessing import normalize


def _auto_eps(X: np.ndarray, k: int = 5, metric: str = "cosine", percentile: float = 10.0) -> float:
    """Estimate eps as a low percentile of k-NN distances.
    Low percentile keeps eps tight, producing meaningful sub-clusters
    rather than one giant cluster — important for high-dim histogram features.
    """
    k = min(k, len(X) - 1)
    nbrs = NearestNeighbors(n_neighbors=k, metric=metric).fit(X)
    dists, _ = nbrs.kneighbors(X)
    kth_dists = np.sort(dists[:, -1])
    return float(np.percentile(kth_dists, percentile))


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
