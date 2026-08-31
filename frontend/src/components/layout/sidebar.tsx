"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Flag, LayoutDashboard, Layers, ScrollText, Sparkles, Users } from "lucide-react";
import { cn } from "@/lib/utils";
import { useAuth } from "@/providers/auth-provider";

const NAV_ITEMS = [
  { href: "/", label: "Dashboard", icon: LayoutDashboard, exact: true },
  { href: "/flags", label: "Feature Flags", icon: Flag, exact: false },
  { href: "/environments", label: "Environments", icon: Layers, exact: false },
  { href: "/audit", label: "Audit Log", icon: ScrollText, exact: false },
] as const;

const ADMIN_NAV_ITEMS = [{ href: "/users", label: "Users", icon: Users, exact: false }] as const;

export function Sidebar() {
  const pathname = usePathname();
  const { user } = useAuth();
  const isAdmin = user?.role === "ADMIN";

  return (
    <aside className="hidden w-60 shrink-0 flex-col border-r border-sidebar-border bg-sidebar text-sidebar-foreground md:flex">
      <div className="flex h-14 items-center gap-2 border-b border-sidebar-border px-4">
        <div className="flex size-7 items-center justify-center rounded-md bg-sidebar-primary text-sidebar-primary-foreground">
          <Flag className="size-4" />
        </div>
        <span className="text-sm font-semibold tracking-tight">Feature Flags</span>
      </div>

      <nav className="flex-1 space-y-0.5 p-2">
        {[...NAV_ITEMS, ...(isAdmin ? ADMIN_NAV_ITEMS : [])].map((item) => {
          const isActive = item.exact ? pathname === item.href : pathname.startsWith(item.href);
          const Icon = item.icon;
          return (
            <Link
              key={item.href}
              href={item.href}
              className={cn(
                "flex items-center gap-2.5 rounded-md px-3 py-2 text-sm font-medium transition-colors",
                isActive
                  ? "bg-sidebar-accent text-sidebar-accent-foreground"
                  : "text-sidebar-foreground/70 hover:bg-sidebar-accent/60 hover:text-sidebar-accent-foreground",
              )}
            >
              <Icon className="size-4" />
              {item.label}
            </Link>
          );
        })}
      </nav>

      {user?.role === "ADMIN" ? (
        <div className="border-t border-sidebar-border p-2">
          <Link
            href="/flags?ai=1"
            className="flex items-center gap-2.5 rounded-md px-3 py-2 text-sm font-medium text-brand-violet transition-colors hover:bg-sidebar-accent/60"
          >
            <Sparkles className="size-4" />
            AI Rule Assistant
          </Link>
        </div>
      ) : null}
    </aside>
  );
}
