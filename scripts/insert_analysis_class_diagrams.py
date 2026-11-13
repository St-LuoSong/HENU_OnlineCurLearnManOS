"""Insert nine approved analysis class diagrams into the existing course SRS."""
from collections import Counter
from copy import deepcopy
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import shutil
import sys

from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Mm, Pt
from PIL import Image

PROJECT = Path(__file__).resolve().parents[1]
SOURCE = PROJECT / "docs/软件需求规格说明书.docx"
ASSETS = PROJECT / "docs/diagrams/AnalysisClass"
WORK = PROJECT / "docs/旧版文件/srs_nine_class_revision"
BACKUP = WORK / "软件需求规格说明书-九张分析类图插入前.docx"
CANDIDATE = WORK / "软件需求规格说明书-九张分析类图稿.docx"
MANIFEST = json.loads((ASSETS / "manifest.json").read_text(encoding="utf-8"))
sys.stdout.reconfigure(encoding="utf-8")

TEXT = {
    21: [
        "**账号与个人中心的职责提炼。**图3-2中的UC-ACC-01至UC-ACC-06、UC-ACC-08及图3-7中的UC-ADMIN-01、UC-ADMIN-02，分别给出登录、资料与偏好维护、安全申请和授权审批的操作及结果。依据FR-AUTH-01、FR-AUTH-02与FR-PROFILE-01、FR-PROFILE-02，识别账号与资料界面、安全申请与审批界面及对应控制类，形成图3-21。",
        "平台用户关联本人的个人偏好及安全申请；审批界面是授权角色的分析视图，不表示学生拥有审批权限。前文图3-8和图3-14要求在业务操作前核对资格，与本图的身份和归属边界一致；安全申请自身的处理职责以其用例规约为依据。积分账本的业务关系在图3-22继续展开。",
    ],
    22: [
        "**由签到交互完善首页与积分模型。**FR-DASH-01、FR-DASH-02规定按本人实际学习交互展示概览，FR-POINT-01、FR-POINT-02规定签到奖励与积分账本。结合图3-1的自主学习范围、图3-2中的UC-ACC-07，以及图3-15的签到积分反馈，识别学习概览控制、签到与积分控制和相应实体，形成图3-22。",
        "图3-15的核对当天状态、确认奖励和反馈结果，分别支撑签到记录、积分账户及签到控制的职责；图3-13的换题费用检查补充积分业务来源。积分账户与流水的关联用于解释有效变动，不表示重复签到可以重复奖励。学习交互记录仅用于声明范围内的统计，练习科目不据此产生课程或班级加入关系。",
    ],
    23: [
        "**由资源访问与维护用例识别分析对象。**FR-RES-01、FR-RES-02与图3-4中的UC-CLASS-02、UC-CLASS-06，以及图3-7中的UC-ADMIN-04、UC-ADMIN-07，共同约束资源查阅、发布维护、分类和附件访问。由这些用例的操作与异常分支识别资源查阅控制、资源维护控制、学习资源及资源附件，形成图3-23。",
        "分类与资源、资源与附件的关联来自归类和引用规则；预览、下载必须沿用资源可见范围。图3-17中的任务提交与反馈说明附件可作为提交证据，但本图仅分析教学资源附件，不能将资源发布视为作业提交或批改。资源归档须保留历史引用依据，任务提交与批改关系仍由图3-19表达。",
    ],
    24: [
        "**由加入资格衔接班级管理与任务交互。**图3-4中的UC-CLASS-01、UC-CLASS-04、UC-CLASS-05与图3-7中的UC-ADMIN-05，对应FR-CLASS-01、FR-CLASS-02，给出创建维护、班级码预览加入及成员状态限制。由此补充班级管理控制、班级成员控制、云班级和科目，形成图3-24。",
        "图3-14在提交前检查有效成员资格，图3-17进一步检验成员限制下的提交分支，因此本图中的班级成员与图3-19是同一业务概念。平台用户通过成员关系参与班级，云班级归属所选科目；科目练习不产生该成员关系。本图说明资格及班级状态的来源，图3-19继续负责提交、批改及反馈，避免重复表达同一流程。",
    ],
    25: [
        "**由配置验证与调用核查用例补充管理职责。**FR-ADMIN-02、FR-ADMIN-03及图3-7中的UC-ADMIN-03、UC-ADMIN-08、UC-ADMIN-10，分别说明模型配置、调用核查和配置可用性验证。依据这些用例识别模型配置控制、调用核查控制、模型供应商、模型配置、计费规则和模型调用记录，形成图3-25。",
        "图3-7中配置流程通过«include»包含验证配置可用性；这是用例关系，不转写为类图中的«include»依赖。图3-8的模式额度核对和图3-13的积分检查提供学生侧交互背景，本图补充管理侧可用配置与规则来源。计费规则不替代积分流水，调用记录也不等于已扣费；配置验证失败须保留原有效配置，敏感凭据不得在图文中明文展示。",
    ],
    26: [
        "**由举报处理与审计用例完善治理模型。**图3-6中的UC-FORUM-04，以及图3-7中的UC-ADMIN-06、UC-ADMIN-07、UC-ADMIN-09，对应FR-FORUM-01、FR-ADMIN-03。依据其授权处理、原因确认、状态反馈和追溯要求，识别内容治理控制、审计查询控制、治理对象、举报记录、治理处理结果与操作审计，形成图3-26。",
        "治理对象可接收多条举报，举报记录最多对应一个当前有效处理结果；实体关联及多重性由受理与处理规则确认。图3-20中的帖子、公示与消息是治理涉及的交流内容，本图补充其治理和追溯职责，不把普通提醒的“已了解”视为治理审批。身份与授权边界沿用图3-21，具体审核与异常处理职责依据相关用例的主成功场景和异常分支确认。",
    ],
}

TRACE_ROWS = [
    ["账号与个人设置", "3-2", "3-21", "5-1、5-11"],
    ["首页、签到与积分", "3-1、3-2", "3-13、3-15、3-22", "5-2、5-11"],
    ["资源查阅与维护", "3-4、3-7", "3-23", "5-3、5-8、5-12"],
    ["问答与知识锦囊", "3-3", "3-8、3-12、3-18", "5-4、5-6"],
    ["套题练习", "3-3", "3-9、3-13、3-16、3-18", "5-5"],
    ["班级与成员管理", "3-4、3-7", "3-14、3-24", "5-7、5-8、5-12"],
    ["任务提交与批改", "3-4", "3-10、3-14、3-17、3-19", "5-7、5-8"],
    ["成长规划", "3-5", "3-11、3-20", "5-9"],
    ["论坛与消息", "3-6", "3-20、3-26", "5-10"],
    ["模型配置与核查", "3-7", "3-8、3-13、3-25", "5-11、5-12"],
    ["平台治理与审计", "3-6、3-7", "3-21、3-26", "5-12"],
]


def replace_text(paragraph, value):
    nodes = paragraph._p.xpath(".//w:t")
    assert nodes
    nodes[0].text = value
    for node in nodes[1:]:
        node.text = ""


def prose(doc, value):
    result = doc.add_paragraph(style="SRS Body")
    for index, part in enumerate(re.split(r"\*\*(.*?)\*\*", value)):
        run = result.add_run(part)
        if index % 2:
            run.bold = True
    result.paragraph_format.keep_with_next = True
    result.paragraph_format.keep_together = True
    return result


def image_paragraph(doc, model, max_height=158):
    path = Path(model["folder"]) / (model["id"] + ".png")
    with Image.open(path) as im:
        width_px, height_px = im.size
    width_mm = min(146, max_height * width_px / height_px)
    p = doc.add_paragraph(style="SRS Caption")
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    pf = p.paragraph_format
    pf.first_line_indent = Pt(0)
    pf.space_before = Pt(4)
    pf.space_after = Pt(0)
    pf.keep_with_next = True
    pf.keep_together = True
    p.add_run().add_picture(str(path), width=Mm(width_mm))
    doc.inline_shapes[-1]._inline.docPr.set("descr", model["title"] + "；«boundary»、«control»、«entity»三栏类框，依赖及关联与多重性。")
    return p


def build():
    WORK.mkdir(parents=True, exist_ok=True)
    if BACKUP.exists() and SOURCE.read_bytes() != BACKUP.read_bytes():
        raise RuntimeError("Source changed since backup; preserve the user's newer edits.")
    if not BACKUP.exists():
        shutil.copy2(SOURCE, BACKUP)
    doc = Document(SOURCE)
    assert len(doc.inline_shapes) == 32 and len(doc.sections) == 1
    models = MANIFEST["models"]
    for model in models[:3]:
        caption = next(p for p in doc.paragraphs if p.text == model["figure"] + " " + model["title"])
        old_image = caption._p.getprevious()
        assert old_image.xpath(".//w:drawing")
        replacement = image_paragraph(doc, model)
        old_image.addprevious(replacement._p)
        old_image.getparent().remove(old_image)
    first_class = next(p for p in doc.paragraphs if p.text.startswith("由用例语义确认实体关联。"))
    replace_text(first_class, first_class.text + "九张子分析类图分别覆盖不同需求域；同名类沿用相同业务含义，边界和控制类按各自用例职责划分。构造型统一为«boundary»、«control»、«entity»，实体包含关联用contains等英文名称表示，不与用例的«include»或«extend»混用。")
    value = first_class.text
    lead = "由用例语义确认实体关联。"
    first_class.clear()
    first_class.add_run(lead).bold = True
    first_class.add_run(value[len(lead):]).bold = False
    initial = next(p for p in doc.paragraphs if p.text.startswith("例如，UC-CLASS-03的任务提交"))
    candidate_note = prose(doc, "账号与安全用例还提出平台用户、个人偏好和安全申请等候选对象；签到、资源与管理用例进一步提出积分、学习资源、模型配置及治理记录。先按用例明确候选职责，再结合下述交互和各用例的异常规则核对，最终在图3-21至图3-26中补充其静态关系。")
    candidate_note.paragraph_format.keep_with_next = False
    initial._p.addnext(candidate_note._p)
    pivot = next(p for p in doc.paragraphs if p.text == "3.3.6 需求与模型对应")
    for model in models[3:]:
        number = int(model["id"].split("_")[1])
        first = True
        for value in TEXT[number]:
            p = prose(doc, value)
            if first:
                p.paragraph_format.page_break_before = True
                first = False
            pivot._p.addprevious(p._p)
        p = image_paragraph(doc, model)
        pivot._p.addprevious(p._p)
        caption = doc.add_paragraph(model["figure"] + " " + model["title"], style="SRS Caption")
        caption.alignment = WD_ALIGN_PARAGRAPH.CENTER
        caption.paragraph_format.keep_with_next = False
        caption.paragraph_format.keep_together = True
        caption.paragraph_format.space_before = Pt(3)
        caption.paragraph_format.space_after = Pt(4)
        pivot._p.addprevious(caption._p)
    summary = next(p for p in doc.paragraphs if p.text.startswith("经过候选对象识别、对象交互检验与类关系提炼"))
    replace_text(summary, "经过候选对象识别、对象交互检验与类关系提炼，图3-18至图3-26形成九张职责互补的分析类图。表3-3分别列出用例范围、交互证据与静态模型及原型位置；其中管理侧职责主要依据用例规约，列出的学生侧交互只说明相关业务背景。修改功能时，应沿此关系检查角色、归属、异常结果与页面权限，保持各模型一致。")
    table = next(t for t in doc.tables if any("交互与分析类图" in c.text for c in t.rows[0].cells))
    row_template = deepcopy(table.rows[1]._tr)
    for row in list(table._tbl.tr_lst)[1:]:
        table._tbl.remove(row)
    for values in TRACE_ROWS:
        row = deepcopy(row_template)
        table._tbl.append(row)
        for cell, value in zip(table.rows[-1].cells, values):
            p = cell.paragraphs[0]
            if p.runs:
                p.runs[0].text = value
                for run in p.runs[1:]:
                    run.text = ""
            else:
                p.add_run(value)
            for extra in list(cell.paragraphs)[1:]:
                cell._tc.remove(extra._p)
    doc.save(CANDIDATE)
    verify()
    print("BUILT", CANDIDATE)


def images(doc):
    return [hashlib.sha256(doc.part.related_parts[s._inline.graphic.graphicData.pic.blipFill.blip.embed].blob).hexdigest()
            for s in doc.inline_shapes]


def verify():
    before, after = Document(BACKUP), Document(CANDIDATE)
    assert len(after.inline_shapes) == 38
    assert len(after.tables) == len(before.tables) == 15
    assert len(after.sections) == 1
    def semantic_xml(element):
        # Word regenerates editing-session IDs and namespace declarations on save.
        attrs = tuple(sorted((k, v) for k, v in element.attrib.items()
                             if not k.rsplit("}", 1)[-1].startswith("rsid")
                             and k.rsplit("}", 1)[-1] not in ["paraId", "textId"]))
        return element.tag, attrs, element.text if element.tag == qn("w:t") else None, tuple(semantic_xml(c) for c in element)
    cover_same = all(semantic_xml(a) == semantic_xml(b) for a, b in zip(list(before._element.body)[:37], list(after._element.body)[:37]))
    assert cover_same, "Cover XML changed"
    old_images, new_images = images(before), images(after)
    assert old_images[:17] == new_images[:17], "Use case/interaction/activity diagrams changed"
    assert old_images[20:] == new_images[26:], "Prototype figures changed"
    for model, digest in zip(MANIFEST["models"], new_images[17:26]):
        expected = hashlib.sha256((Path(model["folder"]) / (model["id"] + ".png")).read_bytes()).hexdigest()
        assert digest == expected, model["id"]
    captions = [p.text for p in after.paragraphs if p.style.name == "SRS Caption" and re.match(r"^图\d+-\d+ ", p.text)]
    assert len(captions) == 38
    assert [re.match(r"图(3-\d+)", value).group(1) for value in captions if value.startswith("图3-")] == [f"3-{n}" for n in range(1, 27)]
    def paragraph_slice(doc, begin, end=None):
        values = [p.text for p in doc.paragraphs if not p.style.name.startswith("toc")]
        i = values.index(begin)
        return values[i:values.index(end)] if end else values[i:]
    assert paragraph_slice(before, "1 引言", "3.3 软件需求的分析模型") == paragraph_slice(after, "1 引言", "3.3 软件需求的分析模型")
    assert paragraph_slice(before, "4 其它软件需求描述") == paragraph_slice(after, "4 其它软件需求描述")
    before_cases = [p.text for p in before.paragraphs if p.style.name == "SRS Case"]
    after_cases = [p.text for p in after.paragraphs if p.style.name == "SRS Case"]
    assert before_cases == after_cases
    full = "\n".join(p.text for p in after.paragraphs)
    defined_uc = set(re.findall(r"^UC-[A-Z]+-\d+", full, re.M))
    defined_fr = set(re.findall(r"^FR-[A-Z]+-\d+", full, re.M))
    for number, notes in TEXT.items():
        for value in notes:
            assert set(re.findall(r"UC-[A-Z]+-\d+", value)) <= defined_uc, number
            assert set(re.findall(r"FR-[A-Z]+-\d+", value)) <= defined_fr, number
    figure_ids = {re.match(r"图(\d+-\d+)", value).group(1) for value in captions}
    assert set(re.findall(r"图(\d+-\d+)", full)) <= figure_ids
    titles = [p.text for p in after.paragraphs if p.style.name == "SRS Heading 3" and p.text.startswith("3.3.")]
    assert titles == ["3.3.1 分析对象初步识别与职责", "3.3.2 顺序图", "3.3.3 序列图", "3.3.4 活动图", "3.3.5 类关系提炼与分析类图完善", "3.3.6 需求与模型对应"]
    result = dict(figures=38, analysis_class_diagrams=9, tables=15, cover_preserved=cover_same,
                  earlier_usecases_unchanged=True, interaction_diagrams_preserved=True,
                  chapters_4_5_preserved=True, diagram_caption_pairing=True,
                  reference_ids_valid=True, sections=1)
    (WORK / "verification.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8", newline="\r\n")
    print("VERIFIED", json.dumps(result, ensure_ascii=False))


def render():
    spec = importlib.util.spec_from_file_location("prior_document_workflow", PROJECT / "scripts/reorder_srs_models.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    module.WORK, module.BACKUP, module.CANDIDATE = WORK, BACKUP, CANDIDATE
    module.render()


def inspect():
    import pymupdf
    from PIL import ImageChops, ImageDraw
    qa = WORK / "qa"
    with pymupdf.open(qa / "before.pdf") as before, pymupdf.open(qa / "after.pdf") as after:
        def raster(page, scale=1.5):
            pix = page.get_pixmap(matrix=pymupdf.Matrix(scale, scale), alpha=False)
            return Image.frombytes("RGB", (pix.width, pix.height), pix.samples)
        assert ImageChops.difference(raster(before[0]), raster(after[0])).getbbox() is None
        locations, overflow, empty_pages = {}, [], []
        for index, page in enumerate(after):
            content = page.get_text()
            for caption in re.findall(r"图3-(?:1[89]|2[0-6])[^\n]*", content):
                if "需求分析类图" in caption:
                    locations[caption] = index + 1
            for block in page.get_text("dict")["blocks"]:
                for line in block.get("lines", []):
                    for span in line["spans"]:
                        x0, y0, x1, y1 = span["bbox"]
                        if x0 < -1 or y0 < -1 or x1 > page.rect.width + 1 or y1 > page.rect.height + 1:
                            overflow.append([index + 1, span["text"]])
            if len(content.strip()) < 5 and not page.get_images():
                empty_pages.append(index + 1)
            raster(page).save(qa / f"page-{index + 1:03}.png")
        assert not overflow, overflow
        assert not empty_pages, empty_pages
        assert len(locations) == 9, locations
        for index in locations.values():
            page = after[index - 1]
            assert page.get_images(), f"Caption separated from diagram on page {index}"
        for start in range(0, len(after), 6):
            canvas = Image.new("RGB", (1680, 1230), "white")
            draw = ImageDraw.Draw(canvas)
            for j, index in enumerate(range(start, min(start + 6, len(after)))):
                im = raster(after[index])
                im.thumbnail((540, 570))
                canvas.paste(im, (j % 3 * 560 + (560 - im.width) // 2, j // 3 * 615 + 23))
                draw.text((j % 3 * 560 + 10, j // 3 * 615 + 5), f"Page {index + 1}", fill="black")
            canvas.save(qa / f"overview-{start // 6 + 1:02}.png")
        result = dict(pages_before=len(before), pages_after=len(after), cover_equal=True,
                      analysis_figure_pages=locations, overflow=overflow, empty_pages=empty_pages)
    (WORK / "layout_review.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8", newline="\r\n")
    verify()
    print(json.dumps(result, ensure_ascii=False, indent=2))


def publish():
    verify()
    review = json.loads((WORK / "layout_review.json").read_text(encoding="utf-8"))
    assert review["cover_equal"] and not review["overflow"] and not review["empty_pages"]
    assert SOURCE.read_bytes() == BACKUP.read_bytes(), "User has edited the source after this revision started."
    pending = SOURCE.with_suffix(".nine-analysis.docx")
    shutil.copy2(CANDIDATE, pending)
    pending.replace(SOURCE)
    print("PUBLISHED", SOURCE)


if __name__ == "__main__":
    actions = {key: globals()[key] for key in ["build", "verify", "render", "inspect", "publish"]}
    actions[sys.argv[1]]()