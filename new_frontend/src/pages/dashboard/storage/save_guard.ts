/**
 * 单次保存闸门：同一时刻只允许一个保存请求进入，避免按钮点击和快捷键重复提交同一份旧版本。
 */
export function createSingleFlightGate(): {
  enter: () => boolean;
  leave: () => void;
} {
  let busy = false;
  return {
    enter(): boolean {
      if (busy) return false;
      busy = true;
      return true;
    },
    leave(): void {
      busy = false;
    },
  };
}
