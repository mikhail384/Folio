import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Folio · подключение холста",
  description: "Закрытое подключение Folio к ChatGPT и Codex из любой сети.",
  icons: {
    icon: "/favicon.svg",
    shortcut: "/favicon.svg",
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ru">
      <body className="antialiased">{children}</body>
    </html>
  );
}
