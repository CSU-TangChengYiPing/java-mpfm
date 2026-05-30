import { describe, expect, it } from "vitest";
import i18n from "./i18n";

describe("auth i18n keys", () => {
  it("provides non-english defaults for login/register copy in zh", () => {
    const t = i18n.getFixedT("zh");
    expect(t("authPages.login.title")).not.toBe("authPages.login.title");
    expect(t("authPages.register.title")).not.toBe("authPages.register.title");
  });
});
