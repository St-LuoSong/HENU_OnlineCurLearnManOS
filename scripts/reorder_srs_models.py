"""Reorder the existing SRS analysis without rebuilding its cover or diagrams."""
from __future__ import annotations

import argparse
from collections import Counter
from copy import deepcopy
import hashlib
import json
from pathlib import Path
import posixpath
import re
import shutil
from zipfile import ZipFile

from lxml import etree


PROJECT = Path(__file__).resolve().parents[1]
SOURCE = PROJECT / "docs/软件需求规格说明书.docx"
WORK = PROJECT / "docs/旧版文件/srs_model_order_revision"
BACKUP = WORK / "软件需求规格说明书-顺序调整前.docx"
CANDIDATE = WORK / "软件需求规格说明书-顺序调整稿.docx"
NS = {"w": "http://schemas.openxmlformats.org/wordprocessingml/2006/main"}
W = "{" + NS["w"] + "}"
OLD_HEADINGS = [
    "3.3.1 分析类识别与职责",
    "3.3.2 需求分析类图",
    "3.3.3 顺序图",
    "3.3.4 序列图",
    "3.3.5 需求与模型对应",
    "3.3.6 活动图",
]
NEW_ORDER = [0, 2, 3, 5, 1, 4]
NEW_HEADINGS = [
    "3.3.1 分析对象初步识别与职责",
    "3.3.2 顺序图",
    "3.3.3 序列图",
    "3.3.4 活动图",
    "3.3.5 类关系提炼与分析类图完善",
    "3.3.6 需求与模型对应",
]
FIGURE_MAP = {
    "3-8": "3-18", "3-9": "3-19", "3-10": "3-20",
    **{f"3-{n}": f"3-{n - 3}" for n in range(11, 19)},
    "3-19": "3-16", "3-20": "3-17",
}
FIGURE_TOKEN = re.compile(
    r"(?<![\dA-Za-z-])3-(?:20|19|18|17|16|15|14|13|12|11|10|9|8)(?![\dA-Za-z-])"
)


def text(element):
    return "".join(element.xpath(".//w:t/text()", namespaces=NS))


def load(path):
    with ZipFile(path) as archive:
        return etree.fromstring(archive.read("word/document.xml"))


def replace_text(element, value):
    """Keep run properties, bookmarks and paragraph properties intact."""
    nodes = element.xpath(".//w:t", namespaces=NS)
    assert nodes, text(element)
    nodes[0].text = value
    for node in nodes[1:]:
        node.text = ""


def paragraph(template, value):
    result = etree.Element(W + "p")
    properties = template.find(W + "pPr")
    if properties is not None:
        result.append(deepcopy(properties))
    for part in re.split(r"(\*\*.*?\*\*)", value):
        if not part:
            continue
        run = etree.SubElement(result, W + "r")
        if part.startswith("**"):
            prop = etree.SubElement(run, W + "rPr")
            etree.SubElement(prop, W + "b")
            part = part[2:-2]
        node = etree.SubElement(run, W + "t")
        node.text = part
    return result


def map_references(root):
    """Replace simultaneously, including references split over formatted runs."""
    for para in root.xpath(".//w:p", namespaces=NS):
        nodes = para.xpath(".//w:t", namespaces=NS)
        content = "".join(node.text or "" for node in nodes)
        tables = para.xpath("ancestor::w:tbl", namespaces=NS)
        traceability = bool(tables and "分析与交互图" in text(tables[0]))
        if "图3-" not in content and not traceability:
            continue
        spans = []
        offset = 0
        for node in nodes:
            length = len(node.text or "")
            spans.append((node, offset, offset + length))
            offset += length
        for match in reversed(list(FIGURE_TOKEN.finditer(content))):
            covered = [(n, a, b) for n, a, b in spans
                       if a < match.end() and b > match.start()]
            for index, (node, start, end) in enumerate(covered):
                left = max(match.start() - start, 0)
                right = min(match.end() - start, end - start)
                original = node.text or ""
                inserted = FIGURE_MAP[match.group()] if index == 0 else ""
                node.text = original[:left] + inserted + original[right:]


def build():
    WORK.mkdir(parents=True, exist_ok=True)
    if BACKUP.exists() and SOURCE.read_bytes() != BACKUP.read_bytes():
        raise RuntimeError("Source changed since backup; preserve the newer document.")
    root = load(SOURCE)
    body = root.find(W + "body")
    children = list(body)
    starts = []
    for title in OLD_HEADINGS:
        found = [i for i, node in enumerate(children) if text(node) == title]
        assert len(found) == 1, (title, found)
        starts.append(found[0])
    end = next(i for i, node in enumerate(children)
               if text(node) == "4 其它软件需求描述")
    blocks = [children[start:stop] for start, stop in
              zip(starts, starts[1:] + [end])]
    template = blocks[0][1]
    new_intro = paragraph(template,
        "本节承接3.2节的用例及其规约，按**初步识别分析对象、验证对象交互、提炼类关系、完善分析类图**的顺序展开。"
        "先从参与者操作和业务规则提出边界、控制与实体对象，再以主成功场景及异常分支核对职责，最后归纳静态关系；各图共同解释同一组需求。")
    for block, title in zip((blocks[i] for i in NEW_ORDER), NEW_HEADINGS):
        replace_text(block[0], title)
    replace_text(blocks[0][1],
        "依据3.2节用例中的参与者操作、业务规则和业务名词，先识别候选分析对象。边界对象接收输入并呈现反馈，"
        "控制对象协调用例流程并核对规则，实体对象表达业务状态与历史事实。此时先明确职责范围，具体操作和关系将在后续交互分析中核对、补充。")
    example = paragraph(template,
        "例如，UC-CLASS-03的任务提交与UC-CLASS-07的教师批改，分别识别任务提交界面、作业批改界面及对应控制对象，"
        "并从参与资格、截止规则、提交记录和评分反馈识别班级成员、学习任务、任务提交、批改结果等实体对象。表3-2列出代表性候选类，后续图逐步检验其职责。")
    blocks[0].append(example)
    replace_text(blocks[0][2], "表3-2 候选分析类与职责摘要")
    replace_text(blocks[2][1],
        "以3.3.1节识别的候选对象作为生命线，分别展开问答、交卷、教学反馈和计划确认的主成功场景。"
        "根据参与者操作逐条安排对象接收的消息，核对边界反馈、控制协调与实体状态的责任归属。消息自上而下表示先后，实线用于请求，虚线用于返回，激活区表示对象处理请求的时间范围。")
    replace_text(blocks[3][1],
        "在前节主成功场景基础上，按课程要求单独编排序列图，进一步展开停止生成、练习换题、任务提交边界和签到反馈。"
        "这些场景用于检验候选对象在取消、失败和重复操作下的职责与状态恢复，不重复主流程；生命线、消息、返回和激活区沿用统一规范。")
    replace_text(blocks[5][1],
        "活动图按参与职责补充业务流转、决策和结束条件，与前述对象交互共同核对用例的完整性。"
        "图3-19对应UC-LEARN-05，说明确认、校验和评阅状态；图3-20以UC-CLASS-03为提交起点，接续UC-CLASS-07教师批改，学生提交成功不代表教师已经完成反馈。")
    derivation = paragraph(template,
        "**由交互消息核对类职责与依赖。**顺序图和序列图中，边界对象向控制对象提交操作，控制对象请求实体核对业务事实，"
        "据此确认相关类的协作依赖，并将接收消息所体现的业务能力归纳为职责。消息只说明某次场景中的协作，不能据此直接认定两个实体存在长期关联。")
    semantics = paragraph(template,
        "**由用例语义确认实体关联。**结合用例规约和活动分支，检查需要长期保留的归属、来源和数量约束。"
        "例如，任务可接收零份或多份提交，一份提交最多有一个当前有效批改结果；该关联及多重性来自业务规则，交互过程用于验证。"
        "将上述职责与关系归并后形成以下分析类图，属性和操作仍采用业务语言，不限定程序或持久化结构。")
    blocks[1][1:1] = [derivation, semantics]
    replace_text(blocks[1][3],
        "图3-11、图3-12、图3-15和图3-16分别验证问答、交卷、停止和换题的协作职责，支持区分问答界面、练习界面及各自控制类。"
        "再结合收藏、历史练习用例，确认问答会话与知识收藏的来源关联，以及练习套题与题目作答的包含关系：收藏保存独立内容，后续生成不得改变旧套题。")
    replace_text(blocks[1][6],
        "图3-13检验教师批改的权限核对和结果形成，图3-17检验成员资格及截止规则，图3-20补充从学生提交到教师反馈的业务衔接。"
        "由此完善任务提交控制、作业批改控制与相关实体的依赖；再依据任务与提交、提交与批改结果的业务语义，确认图中的关联和多重性。")
    replace_text(blocks[1][9],
        "图3-14验证成长周期归属、草案审阅与正式确认，支持明确成长规划控制的职责及其实体依赖。"
        "论坛部分结合3.2节发帖、回复、公示及消息用例确认帖子与回复等业务联系。将动态场景证据和用例语义汇总后完善本图，"
        "成长规划与论坛仍分别协调各自流程；控制依赖表示协作，实体关联表示业务联系。")
    replace_text(blocks[4][1],
        "经过候选对象识别、对象交互检验与类关系提炼，主要需求形成从用例到分析模型再到原型的对应关系。"
        "下表汇总各需求域的定位信息；修改功能时，应沿此关系同步检查用例、动态场景、分析类职责及页面权限，保持名称、规则和范围一致。")
    # Keep the derivation introduction and the final summary off page tails.
    for index in (1, 4):
        props = blocks[index][0].find(W + "pPr")
        if props.find(W + "pageBreakBefore") is None:
            etree.SubElement(props, W + "pageBreakBefore")
    for node in children[starts[0]:end]:
        body.remove(node)
    insertion = starts[0]
    ordered = [new_intro] + [node for i in NEW_ORDER for node in blocks[i]]
    for offset, node in enumerate(ordered):
        body.insert(insertion + offset, node)
    map_references(root)
    trace = blocks[4][-1]
    for row in trace.findall(W + "tr")[1:]:
        cells = row.findall(W + "tc")
        refs = text(cells[2])
        if re.fullmatch(r"3-\d+(?:、3-\d+)*", refs):
            replace_text(cells[2], "、".join(sorted(refs.split("、"), key=lambda v: int(v.split("-")[1]))))
    replace_text(trace.findall(W + "tr")[0].findall(W + "tc")[2], "交互与分析类图")
    if not BACKUP.exists():
        shutil.copy2(SOURCE, BACKUP)
    with ZipFile(SOURCE) as source, ZipFile(CANDIDATE, "w") as target:
        for entry in source.infolist():
            data = (etree.tostring(root, encoding="UTF-8", xml_declaration=True,
                                   standalone=True)
                    if entry.filename == "word/document.xml" else source.read(entry.filename))
            target.writestr(entry, data)
    verify()
    print("BUILT", CANDIDATE)


def verify():
    old, new = load(BACKUP), load(CANDIDATE)
    old_body, new_body = old.find(W + "body"), new.find(W + "body")
    direct = [text(node) for node in new_body]
    positions = [direct.index(title) for title in NEW_HEADINGS]
    assert positions == sorted(positions)
    assert all(sum(value == title for value in direct) == 1 for title in NEW_HEADINGS)
    captions = [value for value in direct if re.match(r"^图\d+-\d+ ", value)]
    assert len(captions) == 32
    assert [re.match(r"图(3-\d+)", value).group(1) for value in captions if value.startswith("图3-")] == [f"3-{i}" for i in range(1, 21)]
    assert len(new.xpath(".//w:tbl", namespaces=NS)) == len(old.xpath(".//w:tbl", namespaces=NS)) == 15
    with ZipFile(BACKUP) as before, ZipFile(CANDIDATE) as after:
        old_media = Counter(hashlib.sha256(before.read(n)).hexdigest()
                            for n in before.namelist() if n.startswith("word/media/") and not n.endswith("/"))
        new_media = Counter(hashlib.sha256(after.read(n)).hexdigest()
                            for n in after.namelist() if n.startswith("word/media/") and not n.endswith("/"))
        assert old_media == new_media, "Diagram content changed."
        def image_order(archive, document):
            rels = etree.fromstring(archive.read("word/_rels/document.xml.rels"))
            targets = {r.get("Id"): r.get("Target") for r in rels}
            embedded = document.xpath(".//a:blip/@r:embed", namespaces={
                "a": "http://schemas.openxmlformats.org/drawingml/2006/main",
                "r": "http://schemas.openxmlformats.org/officeDocument/2006/relationships"})
            return [hashlib.sha256(archive.read(posixpath.normpath(
                posixpath.join("word", targets[rid])))).hexdigest() for rid in embedded]
        old_images = image_order(before, old)
        expected_order = list(range(7)) + list(range(10, 20)) + list(range(7, 10)) + list(range(20, 32))
        assert image_order(after, new) == [old_images[i] for i in expected_order], "Figure/caption pairing changed."
    for label, begin, stop in [
        ("cover", 0, 37),
    ]:
        assert [text(node) for node in list(old_body)[begin:stop]] == [text(node) for node in list(new_body)[begin:stop]], label
    old_text, new_text = text(old), text(new)
    def before_analysis(body):
        values = [text(node) for node in body]
        return values[values.index("1 引言"):values.index("3.3 软件需求的分析模型")]
    assert before_analysis(old_body) == before_analysis(new_body), "Earlier requirements/use cases changed."
    table_captions = lambda body: [text(node) for node in body if re.match(r"^表\d+-\d+ ", text(node))]
    assert [v for v in table_captions(old_body) if not v.startswith("表3-2 ")] == [v for v in table_captions(new_body) if not v.startswith("表3-2 ")], "Table numbering changed."
    old_specs = [text(p) for p in old_body if re.match(r"^UC-[A-Z]+-\d+ ", text(p))]
    new_specs = [text(p) for p in new_body if re.match(r"^UC-[A-Z]+-\d+ ", text(p))]
    assert old_specs == new_specs
    for marker in ["所属角色与需求：", "核心操作与跳转：", "权限、校验与空态："]:
        assert old_text.count(marker) == new_text.count(marker), marker
    def tail_content(body):
        nodes = list(body)
        index = next(i for i, node in enumerate(nodes) if text(node) == "4 其它软件需求描述")
        return [text(node) for node in nodes[index:]]
    assert tail_content(old_body) == tail_content(new_body), "Chapters 4/5 changed."
    report = {"headings": NEW_HEADINGS, "captions": captions,
              "figure_number_mapping": FIGURE_MAP, "figures": 32, "tables": 15,
              "images_unchanged": True, "chapters_4_5_unchanged": True,
              "use_case_specs_unchanged": True}
    (WORK / "verification.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8", newline="\r\n")
    print("VERIFIED: section order, figure references, images and preserved chapters")


def render():
    import win32com.client
    qa = WORK / "qa"
    qa.mkdir(exist_ok=True)
    app = win32com.client.DispatchEx("Word.Application")
    app.Visible = False
    app.DisplayAlerts = 0
    document = None
    try:
        for path, key in [(BACKUP, "before"), (CANDIDATE, "after")]:
            document = app.Documents.Open(str(path), ReadOnly=key == "before", AddToRecentFiles=False)
            if key == "after":
                document.Fields.Update()
                for contents in document.TablesOfContents:
                    contents.Update()
                document.Repaginate()
                for contents in document.TablesOfContents:
                    contents.UpdatePageNumbers()
                document.Save()
            document.ExportAsFixedFormat(str(qa / f"{key}.pdf"), 17, OpenAfterExport=False)
            print("RENDERED", key, document.ComputeStatistics(2), flush=True)
            document.Close(False)
            document = None
    finally:
        if document is not None:
            document.Close(False)
        app.Quit()


def inspect():
    import pymupdf
    from PIL import Image, ImageChops, ImageDraw
    qa = WORK / "qa"
    before = pymupdf.open(qa / "before.pdf")
    after = pymupdf.open(qa / "after.pdf")
    def raster(page):
        pix = page.get_pixmap(matrix=pymupdf.Matrix(1.5, 1.5), alpha=False)
        return Image.frombytes("RGB", (pix.width, pix.height), pix.samples)
    first_old, first_new = raster(before[0]), raster(after[0])
    same = first_old.size == first_new.size and ImageChops.difference(first_old, first_new).getbbox() is None
    assert same, "Cover appearance changed."
    changed, overflow, locations = [], [], {}
    for i, page in enumerate(after):
        content = page.get_text()
        for title in NEW_HEADINGS:
            if title in content:
                locations.setdefault(title, []).append(i + 1)
        if i >= len(before) or content != before[i].get_text():
            changed.append(i + 1)
        for block in page.get_text("dict")["blocks"]:
            for line in block.get("lines", []):
                for span in line["spans"]:
                    x0, y0, x1, y1 = span["bbox"]
                    if x0 < -1 or y0 < -1 or x1 > page.rect.width + 1 or y1 > page.rect.height + 1:
                        overflow.append([i + 1, span["text"]])
        raster(page).save(qa / f"page-{i + 1:03}.png")
    sheets = []
    for start in range(0, len(after), 6):
        canvas = Image.new("RGB", (1680, 1230), "white")
        draw = ImageDraw.Draw(canvas)
        for j, index in enumerate(range(start, min(start + 6, len(after)))):
            im = raster(after[index])
            im.thumbnail((540, 570))
            x = j % 3 * 560 + (560 - im.width) // 2
            y = j // 3 * 615 + 23
            canvas.paste(im, (x, y))
            draw.text((j % 3 * 560 + 10, j // 3 * 615 + 5), f"Page {index + 1}", fill="black")
        out = qa / f"overview-{start // 6 + 1:02}.png"
        canvas.save(out)
        sheets.append(str(out))
    assert not overflow, overflow
    result = {"pages_before": len(before), "pages_after": len(after), "cover_equal": same,
              "changed_pages": changed, "heading_pages": locations, "overflow": overflow,
              "overview_files": sheets}
    (WORK / "layout_review.json").write_text(
        json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8", newline="\r\n")
    verify()
    print(json.dumps(result, ensure_ascii=False, indent=2))


def publish():
    verify()
    review = json.loads((WORK / "layout_review.json").read_text(encoding="utf-8"))
    assert review["cover_equal"] and not review["overflow"]
    assert SOURCE.read_bytes() == BACKUP.read_bytes(), "Source changed since backup; preserve user edits."
    # Keep the previous file and the reviewed candidate in WORK for recovery.
    pending = SOURCE.with_suffix(".reordered.docx")
    shutil.copy2(CANDIDATE, pending)
    pending.replace(SOURCE)
    print("PUBLISHED", SOURCE)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=["build", "render", "inspect", "verify", "publish"])
    args = parser.parse_args()
    globals()[args.action]()