/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_APP_VERSION: string;
  readonly VITE_APP_BUILD_SHA: string;
  readonly VITE_APP_BUILD_TIME: string;
  readonly VITE_APP_ENV: string;
  readonly VITE_APP_NAME?: string;
  readonly VITE_APP_SUBTITLE?: string;
  readonly VITE_APP_COMPANY?: string;
  readonly VITE_APP_LOGO?: string;
  readonly VITE_APP_SUPPORT_URL?: string;
  readonly VITE_APP_DOCS_URL?: string;
  readonly VITE_APP_LICENSE?: string;
  readonly VITE_APP_EDITION?: string;
  readonly VITE_APP_DEMO?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
