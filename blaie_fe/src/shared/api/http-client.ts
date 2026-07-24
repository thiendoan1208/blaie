import axios from "axios";
import { handleHttpError } from "./errors/http-error-handler";
import { normalizeError } from "./errors/normalize-error";
import { prepareCsrfRequest } from "./interceptors/csrf-header";
import { attachRequestIdHeader } from "./interceptors/request-id-header";

export const httpClient = axios.create({
  baseURL:
    process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1",
  timeout: 15000,
  withCredentials: true,
});

httpClient.interceptors.request.use(
  async (config) => {
    config.headers = config.headers ?? {};
    attachRequestIdHeader(config);
    return prepareCsrfRequest(config, httpClient);
  },
  (error) => Promise.reject(normalizeError(error)),
);

httpClient.interceptors.response.use(
  (response) => response,
  (error) => handleHttpError(error, httpClient),
);
