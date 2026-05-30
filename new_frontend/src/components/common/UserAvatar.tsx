import { useState } from "react";

type Props = {
  userId: string;
  nickname?: string;
  avatarUrl?: string;
  size?: number;
  className?: string;
};

export default function UserAvatar({ userId, nickname, avatarUrl, size = 36, className = "" }: Props) {
  const text = (nickname || userId || "U").trim().slice(0, 1).toUpperCase();
  const [failedAvatarUrl, setFailedAvatarUrl] = useState("");
  const showFallback = !avatarUrl || failedAvatarUrl === avatarUrl;

  return (
    <div
      className={`relative shrink-0 overflow-hidden rounded-full border border-white/40 bg-primary/15 text-primary ${className}`}
      style={{ width: size, height: size }}
      title={nickname || userId}
    >
      {avatarUrl ? (
        <img
          src={avatarUrl}
          alt={userId}
          className="h-full w-full object-cover"
          onLoad={() => {
            if (failedAvatarUrl === avatarUrl) setFailedAvatarUrl("");
          }}
          onError={(e) => {
            e.currentTarget.style.display = "none";
            setFailedAvatarUrl(avatarUrl);
          }}
        />
      ) : null}
      {showFallback && <div className="absolute inset-0 flex items-center justify-center text-xs font-semibold">{text}</div>}
    </div>
  );
}
