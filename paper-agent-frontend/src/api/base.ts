export const API_BASE_URL = window.location.port === '5173'
  ? '/api'
  : 'http://localhost:5173/api';

export function apiUrl(path: string): string {
  return `${API_BASE_URL}${path.startsWith('/') ? path : `/${path}`}`;
}
