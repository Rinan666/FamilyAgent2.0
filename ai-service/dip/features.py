import numpy as np
from sklearn.decomposition import PCA
from skimage.feature import local_binary_pattern


class EigenfaceExtractor:
    def __init__(self, n_components: int = 50):
        self.n_components = n_components
        self._pca = PCA(n_components=n_components, whiten=True)

    def fit(self, X: np.ndarray) -> "EigenfaceExtractor":
        self._pca.fit(X)
        return self

    def transform(self, X: np.ndarray) -> np.ndarray:
        return self._pca.transform(X)

    def reconstruct(self, X: np.ndarray) -> np.ndarray:
        return self._pca.inverse_transform(self.transform(X))

    def eigenfaces(self, image_shape: tuple[int, int]) -> np.ndarray:
        """Return eigenfaces as array of shape (n_components, H, W)."""
        return self._pca.components_.reshape(-1, *image_shape)

    def reconstruction_mse(self, X: np.ndarray) -> float:
        reconstructed = self.reconstruct(X)
        return float(np.mean((X - reconstructed) ** 2))


class LBPExtractor:
    """Grid-based LBPH: divide face into grid_size×grid_size cells,
    compute uniform LBP histogram per cell, concatenate.
    Results in grid_size^2 * (n_points+2) dimensional feature vector.
    """
    def __init__(self, radius: int = 1, n_points: int = 8, grid_size: int = 7):
        self.radius = radius
        self.n_points = n_points
        self.grid_size = grid_size
        self._n_bins = n_points + 2

    def fit(self, X: np.ndarray) -> "LBPExtractor":
        return self

    def transform(self, X: np.ndarray) -> np.ndarray:
        h = w = int(np.sqrt(X.shape[1]))
        features = []
        for row in X:
            img = (row.reshape(h, w) * 255).astype(np.uint8)
            lbp = local_binary_pattern(img, self.n_points, self.radius, method="uniform")
            cell_h = h // self.grid_size
            cell_w = w // self.grid_size
            hists = []
            for i in range(self.grid_size):
                for j in range(self.grid_size):
                    cell = lbp[i*cell_h:(i+1)*cell_h, j*cell_w:(j+1)*cell_w]
                    hist, _ = np.histogram(cell.ravel(), bins=self._n_bins,
                                           range=(0, self._n_bins), density=True)
                    hists.append(hist)
            features.append(np.concatenate(hists).astype(np.float32))
        return np.array(features)
