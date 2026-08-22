import { Navigate, Route, Routes } from "react-router-dom";
import { AuthProvider } from "./auth/AuthContext";
import { Layout } from "./components/Layout";
import { ProtectedRoute } from "./components/ProtectedRoute";
import { ToastProvider } from "./components/Toast";
import { CreateDatasetPage } from "./pages/CreateDatasetPage";
import { DatasetHistoryPage } from "./pages/DatasetHistoryPage";
import { DatasetListPage } from "./pages/DatasetListPage";
import { DatasetUploadPage } from "./pages/DatasetUploadPage";
import { LoginPage } from "./pages/LoginPage";

export default function App() {
  return (
    <ToastProvider>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route
            path="/datasets"
            element={
              <ProtectedRoute>
                <Layout>
                  <DatasetListPage />
                </Layout>
              </ProtectedRoute>
            }
          />
          <Route
            path="/datasets/new"
            element={
              <ProtectedRoute>
                <Layout>
                  <CreateDatasetPage />
                </Layout>
              </ProtectedRoute>
            }
          />
          <Route
            path="/datasets/:datasetName/upload"
            element={
              <ProtectedRoute>
                <Layout>
                  <DatasetUploadPage />
                </Layout>
              </ProtectedRoute>
            }
          />
          <Route
            path="/datasets/:datasetName/history"
            element={
              <ProtectedRoute>
                <Layout>
                  <DatasetHistoryPage />
                </Layout>
              </ProtectedRoute>
            }
          />
          <Route path="*" element={<Navigate to="/datasets" replace />} />
        </Routes>
      </AuthProvider>
    </ToastProvider>
  );
}
