import type { Metadata } from "next";
import type { ReactNode } from "react";
import "./globals.css";
import { Providers } from "./providers";
import { Geist } from "next/font/google";
import { cn } from "@/lib/utils";

const geist = Geist({ subsets: ["latin"], variable: "--font-sans" });

export const metadata: Metadata = {
  title: {
    default: "Blaie",
    template: "%s | Blaie",
  },
  description: "Blaie frontend",
  icons: {
    icon: [
      {
        url: "/tab_icon.svg",
        type: "image/svg+xml",
      },
      {
        url: "/tab_logo.png",
        type: "image/png",
      },
    ],
    shortcut: "/tab_icon.svg",
    apple: "/tab_logo.png",
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: ReactNode;
}>) {
  return (
    <html
      lang="en"
      suppressHydrationWarning
      className={cn("font-sans", geist.variable)}
    >
      <body className="min-h-screen bg-background text-foreground antialiased">
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
