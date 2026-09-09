import type { ReactNode } from "react"

import { ThemeProvider } from "@/providers/ThemeProvider"

type AppProviderProps = {
  children: ReactNode
}

/**
 * 应用级 Provider 统一在这里组合，避免入口文件随着认证、请求缓存等能力接入而膨胀。
 */
export function AppProvider({ children }: AppProviderProps) {
  return <ThemeProvider>{children}</ThemeProvider>
}
