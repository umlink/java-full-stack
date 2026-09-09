import { ArrowLeft, MapPinOff } from "lucide-react"
import { Link } from "react-router-dom"

import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"

/** 未匹配路由保持可恢复，避免单页应用在错误地址呈现空白。 */
export function NotFoundPage() {
  return (
    <main className="grid min-h-svh place-items-center bg-muted/30 p-4 sm:p-6">
      <Card className="w-full max-w-sm text-center">
        <CardHeader>
          <MapPinOff aria-hidden="true" className="mx-auto size-8 text-muted-foreground" />
          <CardDescription>404</CardDescription>
          <CardTitle>页面不存在</CardTitle>
        </CardHeader>
        <CardContent>
          <CardDescription>该地址没有对应的管理页面。</CardDescription>
          <Button render={<Link to="/users" />} variant="outline">
            <ArrowLeft aria-hidden="true" />
            返回用户目录
          </Button>
        </CardContent>
      </Card>
    </main>
  )
}
