import axios from "axios";
import { getAccessToken } from "@/shared/auth/auth-storage";
import { normalizeError } from "./normalize-error";
import { createRequestId } from "./request-id";

export const httpClient = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1",
  timeout: 15000,
  withCredentials: true,
});

httpClient.interceptors.request.use(
  (config) => {
    const requestId = createRequestId();
    config.headers = config.headers ?? {};
    Object.assign(config.headers, {
      "X-Request-ID": requestId,
    });

    const accessToken = getAccessToken();
    if (accessToken) {
      Object.assign(config.headers, {
        Authorization: `Bearer ${accessToken}`,
      });
    }

    return config;
  },
  (error) => Promise.reject(normalizeError(error)),
);

httpClient.interceptors.response.use(
  (response) => response,
  (error) => Promise.reject(normalizeError(error)),
);
