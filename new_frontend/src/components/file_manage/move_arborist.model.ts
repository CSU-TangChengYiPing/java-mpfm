import type { FileInfo } from "../../controllers/file_manager";
import { resolveMoveBrowseChildPath, splitMoveBrowseTrail } from "./move_modal.model";

export type MoveArboristNode = {
  id: string;
  name: string;
  path: string;
  hasChildren: boolean;
  loaded: boolean;
  children: MoveArboristNode[];
};

export function createMoveArboristNode(name: string, pathValue: string, hasChildren = false): MoveArboristNode {
  return {
    id: pathValue,
    name,
    path: pathValue,
    hasChildren,
    loaded: false,
    children: [],
  };
}

export function buildMoveArboristNodes(basePath: string, directories: FileInfo[]): MoveArboristNode[] {
  return directories.map((item) => {
    const nextPath = item.path || resolveMoveBrowseChildPath(basePath, item.name);
    return createMoveArboristNode(item.name, nextPath, true);
  });
}

export function patchMoveArboristChildren(nodes: MoveArboristNode[], targetPath: string, children: MoveArboristNode[]): MoveArboristNode[] {
  return nodes.map((node) => {
    if (node.path === targetPath) {
      return {
        ...node,
        loaded: true,
        children,
        hasChildren: children.length > 0,
      };
    }
    if (node.children.length === 0) return node;
    return {
      ...node,
      children: patchMoveArboristChildren(node.children, targetPath, children),
    };
  });
}

export function hasMoveArboristTrail(targetPath: string): boolean {
  return splitMoveBrowseTrail(targetPath).length > 0;
}
