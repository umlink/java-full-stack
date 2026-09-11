import { Users } from "lucide-react"

import { ForbiddenState } from "@/components/ForbiddenState"
import { Badge } from "@/components/ui/badge"
import {
  Card,
  CardAction,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { useUsersService } from "@/pages/Users/useUsersService"

/**
 * 用户目录：M1-C-04 起由后端授权结果驱动页面状态。
 * 就绪态暂保留目录骨架，列表数据渲染在 M1-C-06 接入。
 */
export function UsersPage() {
  const { state, errorMessage } = useUsersService()

  if (state === "forbidden") {
    return <ForbiddenState />
  }

  return (
    <section>
      <div className="flex flex-col justify-between gap-4 border-b pb-6 sm:flex-row sm:items-end">
        <div>
          <p className="text-xs font-medium text-muted-foreground">用户管理</p>
          <h1 className="mt-2 text-3xl font-semibold">用户目录</h1>
          <p className="mt-2 max-w-xl text-sm text-muted-foreground">集中查看平台用户与账号状态。</p>
        </div>
        <Badge className="self-start sm:self-auto" variant="outline">用户目录</Badge>
      </div>

      <Card className="mt-6 rounded-lg" size="sm">
        <CardHeader className="border-b">
          <CardTitle>全部用户</CardTitle>
          <CardAction>
            <Users aria-hidden="true" className="size-4 text-muted-foreground" />
          </CardAction>
        </CardHeader>
        <CardContent aria-busy={state === "loading"} className="gap-0">
          {state === "error" ? (
            <p className="py-5 text-sm text-destructive">{errorMessage}</p>
          ) : (
            [0, 1, 2, 3].map((row) => (
              <div
                className="grid grid-cols-[minmax(9rem,1.4fr)_minmax(7rem,1fr)_minmax(6rem,.8fr)_5rem] items-center gap-6 border-b py-5 last:border-b-0"
                key={row}
              >
                <Skeleton className="h-4 w-4/5" />
                <Skeleton className="h-4 w-3/4" />
                <Skeleton className="h-4 w-2/3" />
                <Skeleton className="h-4 w-full" />
              </div>
            ))
          )}
        </CardContent>
      </Card>
    </section>
  )
}
