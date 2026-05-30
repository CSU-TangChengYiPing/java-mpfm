package com.mpfm.backend.infrastructure.persistence.repository.share.v5;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * v5 授权 SQL 下沉仓储：用于在数据库侧批量计算模板视角的有效权限。
 */
@Repository
public class ShareAuthorizationV5SqlRepository {
    private final JdbcTemplate jdbcTemplate;

    public ShareAuthorizationV5SqlRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** SQL 侧计算出的单路径有效权限行，保留路径与三个布尔位结果。 */
    public record TemplateEffectiveRow(String path, boolean canVisible, boolean canRead, boolean canWrite) { }

    /**
     * 以模板默认值 + 路径特权覆盖为基础，在 SQL 侧完成“父链逐级 AND”计算。
     */
    public Map<String, TemplateEffectiveRow> computeTemplateEffectiveBatch(UUID templateId, List<String> normalizedPaths) {
        if (templateId == null || normalizedPaths == null || normalizedPaths.isEmpty()) {
            return Map.of();
        }
        String valuesSql = buildInputValuesSql(normalizedPaths.size());
        String sql = """
                WITH RECURSIVE input_paths(ord, raw_path) AS (
                  VALUES %s
                ),
                ancestors AS (
                  SELECT ord, raw_path, raw_path AS node_path
                  FROM input_paths
                  UNION ALL
                  SELECT
                    ord,
                    raw_path,
                    CASE
                      WHEN strpos(node_path, '/') = 0 THEN NULL
                      WHEN (length(node_path) - strpos(reverse(node_path), '/') + 1) <= 2 THEN NULL
                      ELSE substring(node_path from 1 for (length(node_path) - strpos(reverse(node_path), '/')))
                    END AS node_path
                  FROM ancestors
                  WHERE node_path IS NOT NULL
                ),
                ancestor_nodes AS (
                  SELECT ord, raw_path, node_path
                  FROM ancestors
                  WHERE node_path IS NOT NULL
                ),
                node_bits AS (
                  SELECT
                    n.ord,
                    n.raw_path,
                    COALESCE(p.allow_visible, t.default_visible) AS can_visible,
                    COALESCE(p.allow_read, t.default_read) AS can_read,
                    COALESCE(p.allow_write, t.default_write) AS can_write
                  FROM ancestor_nodes n
                  JOIN share_role_templates_v5 t
                    ON t.id = ?
                   AND t.state = 'active'
                  LEFT JOIN share_role_template_privileges_v5 p
                    ON p.template_id = t.id
                   AND p.target_path = n.node_path
                )
                SELECT
                  raw_path,
                  COALESCE(bool_and(can_visible), false) AS can_visible,
                  COALESCE(bool_and(can_read), false) AS can_read,
                  COALESCE(bool_and(can_write), false) AS can_write
                FROM node_bits
                GROUP BY ord, raw_path
                ORDER BY ord
                """.formatted(valuesSql);
        List<Object> args = new ArrayList<>();
        args.addAll(normalizedPaths);
        args.add(templateId);
        Map<String, TemplateEffectiveRow> output = new LinkedHashMap<>();
        jdbcTemplate.query(sql, rs -> {
            String path = rs.getString("raw_path");
            output.put(path, new TemplateEffectiveRow(
                    path,
                    rs.getBoolean("can_visible"),
                    rs.getBoolean("can_read"),
                    rs.getBoolean("can_write")));
        }, args.toArray());
        return output;
    }

    private String buildInputValuesSql(int size) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("(").append(i + 1).append(", ?)");
        }
        return sb.toString();
    }

    /**
     * 针对“全部角色均存在模板”的场景，在 SQL 侧完成角色并集 + 父链 AND 计算。
     */
    public Map<String, TemplateEffectiveRow> computeRoleUnionByTemplatesBatch(List<UUID> roleIds, List<String> normalizedPaths) {
        if (roleIds == null || roleIds.isEmpty() || normalizedPaths == null || normalizedPaths.isEmpty()) {
            return Map.of();
        }
        String roleValuesSql = buildSingleColumnValuesSql(roleIds.size());
        String pathValuesSql = buildInputValuesSql(normalizedPaths.size());
        String sql = """
                WITH RECURSIVE input_roles(role_id) AS (
                  VALUES %s
                ),
                input_paths(ord, raw_path) AS (
                  VALUES %s
                ),
                ancestors AS (
                  SELECT ord, raw_path, raw_path AS node_path
                  FROM input_paths
                  UNION ALL
                  SELECT
                    ord,
                    raw_path,
                    CASE
                      WHEN strpos(node_path, '/') = 0 THEN NULL
                      WHEN (length(node_path) - strpos(reverse(node_path), '/') + 1) <= 2 THEN NULL
                      ELSE substring(node_path from 1 for (length(node_path) - strpos(reverse(node_path), '/')))
                    END AS node_path
                  FROM ancestors
                  WHERE node_path IS NOT NULL
                ),
                ancestor_nodes AS (
                  SELECT ord, raw_path, node_path
                  FROM ancestors
                  WHERE node_path IS NOT NULL
                ),
                active_templates AS (
                  SELECT t.id AS template_id, t.role_id, t.default_visible, t.default_read, t.default_write
                  FROM share_role_templates_v5 t
                  JOIN input_roles r ON r.role_id = t.role_id
                  WHERE t.state = 'active'
                ),
                role_node_bits AS (
                  SELECT
                    n.ord,
                    n.raw_path,
                    n.node_path,
                    at.role_id,
                    COALESCE(p.allow_visible, at.default_visible) AS can_visible,
                    COALESCE(p.allow_read, at.default_read) AS can_read,
                    COALESCE(p.allow_write, at.default_write) AS can_write
                  FROM ancestor_nodes n
                  JOIN active_templates at ON TRUE
                  LEFT JOIN share_role_template_privileges_v5 p
                    ON p.template_id = at.template_id
                   AND p.target_path = n.node_path
                ),
                node_union AS (
                  SELECT
                    ord,
                    raw_path,
                    node_path,
                    COALESCE(bool_or(can_visible), false) AS can_visible,
                    COALESCE(bool_or(can_read), false) AS can_read,
                    COALESCE(bool_or(can_write), false) AS can_write
                  FROM role_node_bits
                  GROUP BY ord, raw_path, node_path
                )
                SELECT
                  raw_path,
                  COALESCE(bool_and(can_visible), false) AS can_visible,
                  COALESCE(bool_and(can_read), false) AS can_read,
                  COALESCE(bool_and(can_write), false) AS can_write
                FROM node_union
                GROUP BY ord, raw_path
                ORDER BY ord
                """.formatted(roleValuesSql, pathValuesSql);
        List<Object> args = new ArrayList<>();
        args.addAll(roleIds);
        args.addAll(normalizedPaths);
        Map<String, TemplateEffectiveRow> output = new LinkedHashMap<>();
        jdbcTemplate.query(sql, rs -> {
            String path = rs.getString("raw_path");
            output.put(path, new TemplateEffectiveRow(
                    path,
                    rs.getBoolean("can_visible"),
                    rs.getBoolean("can_read"),
                    rs.getBoolean("can_write")));
        }, args.toArray());
        return output;
    }

    private String buildSingleColumnValuesSql(int size) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("(?)");
        }
        return sb.toString();
    }
}
