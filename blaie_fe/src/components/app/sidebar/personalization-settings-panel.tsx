"use client";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

import { themeChoices } from "./theme-choices";
import type { ThemeChoice } from "./types";

type PersonalizationSettingsPanelProps = {
  selectedTheme: ThemeChoice;
  onSave: () => void;
  onSelectTheme: (theme: ThemeChoice) => void;
};

export function PersonalizationSettingsPanel({
  selectedTheme,
  onSave,
  onSelectTheme,
}: PersonalizationSettingsPanelProps) {
  return (
    <div className="flex-1 flex flex-col justify-between">
      <div className="grid gap-2">
        {themeChoices.map((choice) => (
          <button
            key={choice.value}
            type="button"
            onClick={() => onSelectTheme(choice.value)}
            className={cn(
              "flex h-11 items-center gap-3 rounded-md border px-3 text-left text-sm font-medium transition-colors",
              selectedTheme === choice.value
                ? "border-ring bg-accent text-accent-foreground"
                : "border-border bg-card text-muted-foreground hover:bg-muted hover:text-foreground",
            )}
          >
            <choice.icon className="size-4 shrink-0" />
            <span>{choice.label}</span>
          </button>
        ))}
      </div>

      <div className="mt-8 flex justify-end">
        <Button
          type="button"
          onClick={onSave}
          className="h-10 px-4 text-xs font-semibold cursor-pointer"
        >
          Save appearance
        </Button>
      </div>
    </div>
  );
}
