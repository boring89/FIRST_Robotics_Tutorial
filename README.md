# FIRST_Robotics_Tutorial

給 FRC 新手的 Java 程式教學（WPILib 2027 / Systemcore），網站：<https://boring89.github.io/FIRST_Robotics_Tutorial/>

## 在本機預覽

```bash
pip install -r requirements.txt
mkdocs serve
```

打開 <http://127.0.0.1:8000> 就能看到網站，存檔後會自動重新整理。

## 撰寫

- 內容都在 `docs/`，每章一個資料夾，章節順序在 `mkdocs.yml` 的 `nav` 設定
- 寫作風格請看 [寫作規範](docs/appendix/style-guide.md)
- 共用的提示段落放在 `includes/`，用 `--8<-- "檔名.md"` 引用

## 部署

push 到 `main` 後，GitHub Actions 會自動建置並部署到 GitHub Pages。第一次使用前，到 repo 的 **Settings → Pages**，把 **Source** 設成 **GitHub Actions**。
