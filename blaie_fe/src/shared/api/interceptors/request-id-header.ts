import type { InternalAxiosRequestConfig } from "axios";
import { createRequestId } from "../utils/request-id";

export function attachRequestIdHeader(config: InternalAxiosRequestConfig) {
  Object.assign(config.headers, {
    "X-Request-ID": createRequestId(),
  });
}
