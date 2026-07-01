import type { ThemeChoice } from "./types";

export function getInitials(name?: string) {
  if (!name) {
    return "U";
  }

  return name.trim().charAt(0).toUpperCase();
}

export function toThemeChoice(theme?: string): ThemeChoice {
  return theme === "light" || theme === "dark" || theme === "system"
    ? theme
    : "system";
}
