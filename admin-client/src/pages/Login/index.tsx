import { ArrowRight, ShieldCheck } from "lucide-react"
import { Link } from "react-router-dom"

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
import { Separator } from "@/components/ui/separator"

/** 登录页在本卡仅建立界面与路由；提交、会话与跳转由 M1-C-02 实现。 */
export function LoginPage() {
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
          <div className="grid gap-2">
            <Label htmlFor="username">用户名</Label>
            <Input disabled id="username" placeholder="请输入用户名" />
          </div>
          <div className="grid gap-2">
            <Label htmlFor="password">密码</Label>
            <Input disabled id="password" placeholder="请输入密码" type="password" />
          </div>
          <Button disabled>登录</Button>
          <Separator />
          <Button render={<Link to="/users" />} variant="ghost">
            返回管理工作台
            <ArrowRight aria-hidden="true" />
          </Button>
        </CardContent>
      </Card>
    </main>
  )
}
