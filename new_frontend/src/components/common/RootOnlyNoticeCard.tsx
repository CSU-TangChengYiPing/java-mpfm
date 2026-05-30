import { Card, CardBody } from "@heroui/card";
import type { ReactNode } from "react";

type RootOnlyNoticeCardProps = {
  message: ReactNode;
};

export default function RootOnlyNoticeCard({ message }: RootOnlyNoticeCardProps) {
  return (
    <Card className="bg-white/65">
      <CardBody>
        <p className="text-sm text-default-500">{message}</p>
      </CardBody>
    </Card>
  );
}

