import { useMemo } from "react";

type BlobConfig = {
  size: number;
  left: number;
  top: number;
  hue: number;
  alpha: number;
  blur: number;
  duration: number;
  delay: number;
  driftX: number;
  driftY: number;
};

function rand(min: number, max: number): number {
  return Math.random() * (max - min) + min;
}

function createBlobs(): BlobConfig[] {
  const count = 6;
  return Array.from({ length: count }).map(() => ({
    size: rand(260, 560),
    left: rand(-12, 78),
    top: rand(-18, 70),
    hue: rand(190, 330),
    alpha: rand(0.2, 0.38),
    blur: rand(70, 125),
    duration: rand(24, 46),
    delay: rand(-16, 0),
    driftX: rand(-42, 42),
    driftY: rand(-36, 36),
  }));
}

export default function PageBackground() {
  const blobs = useMemo(() => createBlobs(), []);

  return (
    <div className="pointer-events-none fixed inset-0 z-0 h-full w-full overflow-hidden bg-gradient-to-br from-indigo-50 via-white to-pink-50 dark:from-gray-900 dark:via-gray-800 dark:to-gray-900">
      {blobs.map((blob, idx) => (
        <div
          key={`bg-blob-${idx}`}
          className="page-bg-blob absolute rounded-full"
          style={{
            width: `${blob.size}px`,
            height: `${blob.size}px`,
            left: `${blob.left}%`,
            top: `${blob.top}%`,
            backgroundColor: `hsla(${blob.hue}, 85%, 74%, ${blob.alpha})`,
            filter: `blur(${blob.blur}px)`,
            animationDuration: `${blob.duration}s`,
            animationDelay: `${blob.delay}s`,
            ["--drift-x" as string]: `${blob.driftX}px`,
            ["--drift-y" as string]: `${blob.driftY}px`,
          }}
        />
      ))}
    </div>
  );
}
