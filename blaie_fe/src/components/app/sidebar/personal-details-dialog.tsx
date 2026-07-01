"use client";

import { X } from "lucide-react";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import type { AuthUser } from "@/features/auth/types/types";

import { getInitials } from "./utils";

type PersonalDetailsDialogProps = {
  displayName: string;
  formattedCreatedAt: string;
  open: boolean;
  user: AuthUser;
  onOpenChange: (open: boolean) => void;
};

export function PersonalDetailsDialog({
  displayName,
  formattedCreatedAt,
  open,
  user,
  onOpenChange,
}: PersonalDetailsDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        showCloseButton={false}
        className="max-w-lg overflow-hidden border border-border bg-card p-0 text-card-foreground"
      >
        <DialogClose asChild>
          <Button
            variant="ghost"
            size="icon-sm"
            className="absolute top-4 right-4 text-muted-foreground hover:bg-muted hover:text-foreground"
          >
            <X className="size-4" />
            <span className="sr-only">Close</span>
          </Button>
        </DialogClose>
        <div className="p-5">
          <DialogHeader>
            <DialogTitle>Personal details</DialogTitle>
            <DialogDescription>
              Review the profile information currently attached to your account.
            </DialogDescription>
          </DialogHeader>

          <div className="mt-5 flex items-center gap-4 rounded-lg border border-border bg-background p-4">
            <div className="flex size-12 shrink-0 select-none items-center justify-center rounded-full border border-border bg-muted text-sm font-semibold text-muted-foreground">
              {getInitials(displayName)}
            </div>
            <div className="min-w-0">
              <p className="truncate text-sm font-semibold">{displayName}</p>
              <p className="truncate text-xs text-muted-foreground">
                {user.email}
              </p>
            </div>
          </div>

          <div className="mt-5 grid gap-3 text-sm">
            <div className="grid grid-cols-[8rem_minmax(0,1fr)] gap-3">
              <span className="text-muted-foreground">Display name</span>
              <span className="truncate font-medium">
                {user.displayName || "Not set"}
              </span>
            </div>
            <div className="grid grid-cols-[8rem_minmax(0,1fr)] gap-3">
              <span className="text-muted-foreground">Username</span>
              <span className="truncate font-medium">
                {user.username || "Not set"}
              </span>
            </div>
            <div className="grid grid-cols-[8rem_minmax(0,1fr)] gap-3">
              <span className="text-muted-foreground">Email</span>
              <span className="truncate font-medium">{user.email}</span>
            </div>
            <div className="grid grid-cols-[8rem_minmax(0,1fr)] gap-3">
              <span className="text-muted-foreground">Email status</span>
              <span className="font-medium">
                {user.emailVerified ? "Verified" : "Not verified"}
              </span>
            </div>
            <div className="grid grid-cols-[8rem_minmax(0,1fr)] gap-3">
              <span className="text-muted-foreground">Password</span>
              <span className="font-medium">
                {user.hasPassword ? "Set" : "Not set"}
              </span>
            </div>
            <div className="grid grid-cols-[8rem_minmax(0,1fr)] gap-3">
              <span className="text-muted-foreground">Joined</span>
              <span className="truncate font-medium">{formattedCreatedAt}</span>
            </div>
            <div className="grid grid-cols-[8rem_minmax(0,1fr)] gap-3">
              <span className="text-muted-foreground">User ID</span>
              <span className="truncate font-mono text-xs">{user.id}</span>
            </div>
          </div>
        </div>

        <div className="flex justify-end border-t border-border bg-muted/40 px-5 py-3">
          <DialogClose asChild>
            <Button variant="outline">Close</Button>
          </DialogClose>
        </div>
      </DialogContent>
    </Dialog>
  );
}
