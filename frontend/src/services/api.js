/**
 * Centralized Axios API Client for Scam Shield FastAPI Backend.
 * Holds bearer token strictly in memory (never in localStorage).
 */

import axios from 'axios';
import { auth } from './firebase';

export const DEFAULT_API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL || 'https://scamshield-4ati.onrender.com';

// Strictly in-memory token and API base override
let memoryToken = null;
let runtimeApiBaseUrl = DEFAULT_API_BASE_URL;

export function setInMemoryAuthToken(token) {
  memoryToken = token ? token.trim() : null;
}

export function getInMemoryAuthToken() {
  return memoryToken;
}

export function setApiBaseUrl(url) {
  runtimeApiBaseUrl = url ? url.trim().replace(/\/+$/, '') : DEFAULT_API_BASE_URL;
}

export function getApiBaseUrl() {
  return runtimeApiBaseUrl;
}

const apiClient = axios.create({
  baseURL: runtimeApiBaseUrl,
  timeout: 60000,
});

// Request Interceptor: Resolve token from Firebase SDK or memory
apiClient.interceptors.request.use(
  async (config) => {
    config.baseURL = runtimeApiBaseUrl;

    let token = memoryToken;
    if (!token && auth?.currentUser) {
      try {
        token = await auth.currentUser.getIdToken();
      } catch {
        token = null;
      }
    }

    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response Interceptor: Normalize FastAPI error envelopes
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (!error.response) {
      const networkErr = new Error(
        `Cannot reach Scam Shield backend at ${runtimeApiBaseUrl}. Ensure FastAPI/Uvicorn is running.`
      );
      networkErr.status = 0;
      return Promise.reject(networkErr);
    }

    const status = error.response.status;
    const data = error.response.data;

    let message = 'An unexpected server error occurred.';
    if (data?.error?.message) {
      message = data.error.message;
      if (Array.isArray(data.error.details) && data.error.details.length > 0) {
        message += ` (${data.error.details.join('; ')})`;
      }
    } else if (typeof data?.detail === 'string') {
      message = data.detail;
    } else if (Array.isArray(data?.detail)) {
      message = data.detail.map((d) => d.msg || JSON.stringify(d)).join('; ');
    }

    const normalizedError = new Error(message);
    normalizedError.status = status;
    normalizedError.data = data;
    return Promise.reject(normalizedError);
  }
);

export const scamShieldApi = {
  async getSystemInfo() {
    const { data } = await apiClient.get('/');
    return data;
  },

  async getHealth() {
    const { data } = await apiClient.get('/health');
    return data;
  },

  async verifyToken(token) {
    const { data } = await apiClient.post('/api/auth/verify', { token });
    return data;
  },

  async getCurrentUser() {
    const { data } = await apiClient.get('/api/auth/me');
    return data;
  },

  async uploadAudio(file, onProgress) {
    const formData = new FormData();
    formData.append('file', file);
    const { data } = await apiClient.post('/api/audio/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: (evt) => {
        if (onProgress && evt.total) {
          onProgress(Math.round((evt.loaded * 100) / evt.total));
        }
      },
    });
    return data;
  },

  async getAudioStatus(audioId) {
    const { data } = await apiClient.get(`/api/audio/${audioId}`);
    return data;
  },

  async deleteAudio(audioId) {
    const { data } = await apiClient.delete(`/api/audio/${audioId}`);
    return data;
  },

  async analyzeAudioById(audioId) {
    const formData = new FormData();
    formData.append('audio_id', audioId);
    const { data } = await apiClient.post('/api/analyze/audio', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return data;
  },

  async analyzeAudioFile(file) {
    const formData = new FormData();
    formData.append('file', file);
    const { data } = await apiClient.post('/api/analyze/audio', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return data;
  },

  async analyzeText(text) {
    const { data } = await apiClient.post('/api/analyze/text', { text });
    return data;
  },

  async getHistory() {
    const { data } = await apiClient.get('/api/history');
    return data;
  },

  async getHistoryDetail(analysisId) {
    const { data } = await apiClient.get(`/api/history/${analysisId}`);
    return data;
  },

  async deleteHistoryRecord(analysisId) {
    const { data } = await apiClient.delete(`/api/history/${analysisId}`);
    return data;
  },

  async getContacts() {
    const { data } = await apiClient.get('/api/contacts');
    return data;
  },

  async createContact(payload) {
    const { data } = await apiClient.post('/api/contacts', payload);
    return data;
  },

  async deleteContact(contactId) {
    const { data } = await apiClient.delete(`/api/contacts/${contactId}`);
    return data;
  },
};

export default apiClient;

