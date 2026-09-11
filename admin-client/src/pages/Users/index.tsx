import { cn } from "cn"
import { RefreshCw, Users } from "lucide-react"

import { ForbiddenState } from "@/components/ForbiddenState"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardAction,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { useUsersService } from "@/pages/Users/useUsersService"

/**
 * 用户目录：M1-C-04 起由后端授权结果驱动页面状态，M1-C-06 接入列表数据渲染。
 * 表格按列展示允许字段，覆盖加载 / 空 / 错误 / 无权限分支，并提供手动刷新。
 */
export function UsersPage() {
  const { state, errorMessage, rows, isRefreshing, refresh } = useUsersService()

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
            <Button
              aria-label="刷新用户列表"
              disabled={isRefreshing}
              onClick={refresh}
              size="icon"
              variant="ghost"
            >
              <RefreshCw
                aria-hidden="true"
                className={cn("size-4", isRefreshing && "animate-spin")}
              />
            </Button>
          </CardAction>
        </CardHeader>
        <CardContent aria-busy={state === "loading"} className="gap-0">
          {state === "loading" ? (
            // 骨架列宽对齐就绪态表格，保证状态切换时行高与间距稳定
            [0, 1, 2, 3].map((row) => (
              <div
                className="grid grid-cols-[5rem_minmax(9rem,1.4fr)_minmax(7rem,1fr)_minmax(10rem,1.6fr)_6rem_minmax(9rem,1fr)] items-center gap-6 border-b px-2 py-5 last:border-b-0"
                key={row}
              >
                <Skeleton className="h-4 w-2/3" />
                <Skeleton className="h-4 w-4/5" />
                <Skeleton className="h-4 w-3/4" />
                <Skeleton className="h-4 w-4/5" />
                <Skeleton className="h-4 w-1/2" />
                <Skeleton className="h-4 w-3/4" />
              </div>
            ))
          ) : state === "empty" ? (
            <div className="flex flex-col items-center gap-2 py-10 text-muted-foreground">
              <Users aria-hidden="true" className="size-6" />
              <p className="text-sm">暂无用户</p>
            </div>
          ) : state === "error" ? (
            <p className="py-5 text-sm text-destructive">{errorMessage}</p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>ID</TableHead>
                  <TableHead>用户名</TableHead>
                  <TableHead>姓名</TableHead>
                  <TableHead>邮箱</TableHead>
                  <TableHead>状态</TableHead>
                  <TableHead>创建时间</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {rows.map((row) => (
                  <TableRow key={row.id}>
                    <TableCell>{row.id}</TableCell>
                    {/* 用户名/邮箱可能超长：截断展示 + title 完整值，文本不得溢出表格 */}
                    <TableCell className="max-w-[16rem] truncate" title={row.username}>
                      {row.username}
                    </TableCell>
                    <TableCell>{row.name}</TableCell>
                    <TableCell className="max-w-[16rem] truncate" title={row.email}>
                      {row.email}
                    </TableCell>
                    <TableCell>
                      <Badge variant={row.statusTone}>{row.statusLabel}</Badge>
                    </TableCell>
                    <TableCell>{row.createTime}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
    </section>
  )
}
