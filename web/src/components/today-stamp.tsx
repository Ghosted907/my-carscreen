import { useEffect, useState } from "react";
import { stampFromDate } from "@/lib/cars";

export function TodayStamp() {
  const [stamp, setStamp] = useState<string | null>(null);

  useEffect(() => {
    const update = () => setStamp(stampFromDate(new Date()));
    update();
    const id = window.setInterval(update, 60_000);
    return () => window.clearInterval(id);
  }, []);

  return (
    <span className="font-mono text-copper" dir="ltr">
      {stamp ?? "········"}
    </span>
  );
}
