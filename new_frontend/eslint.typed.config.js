import js from "@eslint/js";
import eslintComments from "@eslint-community/eslint-plugin-eslint-comments";
import globals from "globals";
import i18next from "eslint-plugin-i18next";
import jsdoc from "eslint-plugin-jsdoc";
import reactHooks from "eslint-plugin-react-hooks";
import reactRefresh from "eslint-plugin-react-refresh";
import tseslint from "typescript-eslint";

export default tseslint.config(
  {
    ignores: ["dist", "node_modules"],
  },
  {
    files: ["src/**/*.{ts,tsx}"],
    extends: [
      js.configs.recommended,
      ...tseslint.configs.recommended,
      ...tseslint.configs.recommendedTypeChecked,
      reactHooks.configs.flat["recommended-latest"],
    ],
    languageOptions: {
      ecmaVersion: 2021,
      globals: globals.browser,
      parserOptions: {
        projectService: true,
      },
    },
    plugins: {
      "eslint-comments": eslintComments,
      i18next,
      jsdoc,
      "react-refresh": reactRefresh,
    },
    rules: {
      "@typescript-eslint/no-explicit-any": "error",
      "@typescript-eslint/ban-ts-comment": ["error", { "ts-ignore": "allow-with-description", "ts-expect-error": "allow-with-description", minimumDescriptionLength: 8 }],
      "eslint-comments/disable-enable-pair": ["error", { allowWholeFile: true }],
      "eslint-comments/no-unlimited-disable": "error",
      "eslint-comments/require-description": "error",
      "jsdoc/check-param-names": "error",
      "jsdoc/check-tag-names": "error",
      "jsdoc/require-description": "error",
      "jsdoc/require-param-name": "error",
      "jsdoc/require-returns-check": "error",
      "react-hooks/set-state-in-effect": "off",
      "react-hooks/exhaustive-deps": "off",
      "i18next/no-literal-string": "off",
      "react-refresh/only-export-components": "off",
    },
  }
  ,
  {
    files: ["src/controllers/**/*.ts", "src/hooks/**/*.ts", "src/utils/**/*.ts", "src/pages/**/*.ts"],
    ignores: ["**/*.test.ts", "**/*.test.tsx"],
    rules: {
      "jsdoc/require-jsdoc": ["error", {
        contexts: [
          "FunctionDeclaration",
          "ClassDeclaration"
        ],
        publicOnly: true,
        require: {
          FunctionDeclaration: true,
          ClassDeclaration: true,
          MethodDefinition: false
        }
      }]
    }
  }
);
