import { LogOut, ShieldOff } from "lucide-react"

import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { useLogout } from "@/hooks/useLogout"

/**
 * 受保护资源返回 403 时的可恢复状态。
 *
 * 会话被刻意保留（无权限 ≠ 登录失效）；文案与后端口径一致，不出现
 * 角色、权限码或异常细节。对无后台权限的账号，管理端不存在可安全
 * 返回的默认页，唯一恢复出口是退出登录换号。
 */
export function ForbiddenState() {
  const logout = useLogout()

  return (
    <Card className="mx-auto w-full max-w-sm text-center" size="sm">
      <CardHeader>
        <ShieldOff aria-hidden="true" className="mx-auto size-8 text-muted-foreground" />
        <CardTitle>无访问权限</CardTitle>
        <CardDescription>当前账号没有管理后台的访问权限。</CardDescription>
      </CardHeader>
      <CardContent>
        <Button onClick={logout} variant="outline">
          <LogOut aria-hidden="true" />
          退出登录
        </Button>
      </CardContent>
    </Card>
  )
}
