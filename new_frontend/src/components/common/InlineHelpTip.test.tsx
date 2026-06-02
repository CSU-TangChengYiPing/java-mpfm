import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import InlineHelpTip from "./InlineHelpTip";

describe("InlineHelpTip", () => {
  it("renders a clickable help trigger", () => {
    const markup = renderToStaticMarkup(<InlineHelpTip ariaLabel="帮助说明" content="这里是帮助内容" />);
    expect(markup).toContain('aria-label="帮助说明"');
    expect(markup).toContain("?");
  });
});
