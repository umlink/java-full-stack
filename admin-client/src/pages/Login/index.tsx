import { Loader2, ShieldCheck } from "lucide-react"

import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { useLoginService } from "@/pages/Login/useLoginService"

/** 登录页：字段校验、提交中、失败提示与成功跳转由 useLoginService 编排。 */
export function LoginPage() {
  const loginService = useLoginService()

  return (
    <main className="grid min-h-svh place-items-center bg-muted/30 p-4 sm:p-6">
      <Card className="w-full max-w-md">
        <CardHeader>
          <div className="mb-2 flex size-10 items-center justify-center rounded-md bg-primary text-primary-foreground">
            <ShieldCheck aria-hidden="true" className="size-5" />
          </div>
          <CardTitle>登录管理后台</CardTitle>
          <CardDescription>使用管理账号进入运营工作台。</CardDescription>
        </CardHeader>
        <CardContent className="gap-5">
          <form
            className="grid gap-5"
            onSubmit={(event) => {
              // 阻止原生提交刷新页面；submit 自带空值拦截，不会发出明显无效请求
              event.preventDefault()
              void loginService.submit({
                username: loginService.username,
                password: loginService.password,
              })
            }}
          >
            <div className="grid gap-2">
              <Label htmlFor="username">用户名</Label>
              <Input
                aria-invalid={loginService.fieldErrors.username != null}
                autoComplete="username"
                id="username"
                onChange={(event) => loginService.handleUsernameChange(event.target.value)}
                placeholder="请输入用户名"
                value={loginService.username}
              />
              {loginService.fieldErrors.username && (
                <p className="text-sm text-destructive">{loginService.fieldErrors.username}</p>
              )}
            </div>
            <div className="grid gap-2">
              <Label htmlFor="password">密码</Label>
              <Input
                aria-invalid={loginService.fieldErrors.password != null}
                autoComplete="current-password"
                id="password"
                onChange={(event) => loginService.handlePasswordChange(event.target.value)}
                placeholder="请输入密码"
                type="password"
                value={loginService.password}
              />
              {loginService.fieldErrors.password && (
                <p className="text-sm text-destructive">{loginService.fieldErrors.password}</p>
              )}
            </div>
            <Button disabled={loginService.isSubmitting} type="submit">
              {loginService.isSubmitting && (
                <Loader2 aria-hidden="true" className="animate-spin" />
              )}
              {loginService.isSubmitting ? "登录中…" : "登录"}
            </Button>
            {loginService.errorMessage && (
              <p className="text-sm text-destructive">{loginService.errorMessage}</p>
            )}
          </form>
        </CardContent>
      </Card>
    </main>
  )
}
