import i18n from "../../i18n";

export type AvatarLikeFile = {
  type: string;
  size: number;
};

export function validateAvatarFile(file: AvatarLikeFile | null | undefined): string | null {
  if (!file) return i18n.t("avatar.invalidType");
  if (!file.type.startsWith("image/")) return i18n.t("avatar.invalidType");
  if (file.size > 8 * 1024 * 1024) return i18n.t("avatar.tooLarge");
  return null;
}
