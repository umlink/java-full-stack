import { LogOut, Store, Users } from "lucide-react"
import { Link, NavLink, Outlet, useLocation } from "react-router-dom"

import {
  Avatar,
  AvatarBadge,
  AvatarFallback,
} from "@/components/ui/avatar"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarInset,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarProvider,
  SidebarRail,
  SidebarTrigger,
} from "@/components/ui/sidebar"
import { useLogout } from "@/hooks/useLogout"

const navigationItems = [
  { label: "用户", icon: Users, to: "/users" },
]

/** 管理后台使用 shadcn Sidebar 承载导航，业务页面仅填充 Outlet 内容区域。 */
export function AdminLayout() {
  const { pathname } = useLocation()
  const logout = useLogout()

  return (
    <SidebarProvider>
      <Sidebar collapsible="icon">
        <SidebarHeader>
          <Link className="flex items-center gap-2 px-2 py-1" to="/users">
            <span className="grid size-8 place-items-center rounded-md bg-primary text-primary-foreground">
              <Store aria-hidden="true" className="size-4" />
            </span>
            <span className="font-semibold">BOOTMALL</span>
          </Link>
        </SidebarHeader>
        <SidebarContent>
          <SidebarGroup>
            <SidebarGroupLabel>工作区</SidebarGroupLabel>
            <SidebarGroupContent>
              <SidebarMenu>
                {navigationItems.map(({ icon: Icon, label, to }) => (
                  <SidebarMenuItem key={to}>
                    <SidebarMenuButton
                      isActive={pathname === to}
                      render={<NavLink to={to} />}
                      tooltip={label}
                    >
                      <Icon aria-hidden="true" />
                      <span>{label}</span>
                    </SidebarMenuButton>
                  </SidebarMenuItem>
                ))}
              </SidebarMenu>
            </SidebarGroupContent>
          </SidebarGroup>
        </SidebarContent>
        <SidebarFooter>
          <Badge variant="secondary">管理控制台</Badge>
          <Button className="justify-start" onClick={logout} variant="ghost">
            <LogOut aria-hidden="true" />
            退出登录
          </Button>
        </SidebarFooter>
        <SidebarRail />
      </Sidebar>

      <SidebarInset>
        <header className="flex h-14 items-center justify-between border-b px-4 sm:px-6">
          <div className="flex items-center gap-3">
            <SidebarTrigger aria-label="切换导航" />
            <div>
              <p className="text-sm font-medium">运营工作台</p>
              <p className="text-xs text-muted-foreground">管理后台</p>
            </div>
          </div>
          <Avatar aria-label="管理员" size="sm">
            <AvatarFallback>AD</AvatarFallback>
            <AvatarBadge aria-hidden="true" />
          </Avatar>
        </header>
        <div className="mx-auto w-full max-w-7xl px-4 py-6 sm:px-6 lg:py-8">
          <Outlet />
        </div>
      </SidebarInset>
    </SidebarProvider>
  )
}
