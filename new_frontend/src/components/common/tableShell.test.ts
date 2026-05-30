import { describe, expect, it } from "vitest";
import { sharedGlassTableClassNames, sharedTableBottomBarClassName } from "./tableShell";

describe("tableShell", () => {
  it("uses the shared glass table wrapper style", () => {
    expect(sharedGlassTableClassNames.wrapper).toContain("backdrop-blur-xl");
    expect(sharedGlassTableClassNames.wrapper).toContain("h-[calc(100vh-264px)]");
    expect(sharedGlassTableClassNames.thead).toContain("sticky");
    expect(sharedTableBottomBarClassName).toContain("backdrop-blur-xl");
  });
});
