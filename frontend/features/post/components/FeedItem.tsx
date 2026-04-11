import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import type { Post } from "@/features/post/types";

interface FeedItemProps {
  post: Post;
}

function formatRelativeTime(date: Date): string {
  const diffSec = Math.floor((Date.now() - date.getTime()) / 1000);
  if (diffSec < 60) return "방금 전";
  if (diffSec < 3600) return `${Math.floor(diffSec / 60)}분 전`;
  if (diffSec < 86400) return `${Math.floor(diffSec / 3600)}시간 전`;
  if (diffSec < 86400 * 7) return `${Math.floor(diffSec / 86400)}일 전`;
  return date.toLocaleDateString("ko-KR");
}

export function FeedItem({ post }: FeedItemProps) {
  const initial = post.author.nickname.slice(0, 1);
  return (
    <article className="border-border flex gap-3 border-b px-4 py-4">
      <Avatar className="h-10 w-10 shrink-0">
        <AvatarFallback>{initial}</AvatarFallback>
      </Avatar>
      <div className="min-w-0 flex-1">
        <header className="flex items-baseline gap-2">
          <span className="truncate font-semibold">{post.author.nickname}</span>
          <span className="text-muted-foreground text-xs">
            {formatRelativeTime(post.createdAt)}
          </span>
        </header>
        <p className="mt-1 break-words whitespace-pre-wrap">{post.content}</p>
      </div>
    </article>
  );
}
