import { Monitor, Moon, Sun, type LucideIcon } from "lucide-react";

import type { ThemeChoice } from "./types";

export const themeChoices: Array<{
  value: ThemeChoice;
  label: string;
  icon: LucideIcon;
}> = [
  { value: "system", label: "System", icon: Monitor },
  { value: "light", label: "Light", icon: Sun },
  { value: "dark", label: "Dark", icon: Moon },
];
