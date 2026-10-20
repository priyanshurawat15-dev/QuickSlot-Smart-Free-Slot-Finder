"""Render the maintained Spring Boot roadmap as a compact learning PDF."""
from pathlib import Path
import html
import re

from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.platypus import (
    SimpleDocTemplate, Paragraph, Spacer, PageBreak, Preformatted, Flowable,
)

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'docs/learning/spring-boot-roadmap.md'
OUTPUT = ROOT / 'output/pdf/QuickSlot_Spring_Boot_Learning_Guide.pdf'
RED = colors.HexColor('#AF2029')
INK = colors.HexColor('#161616')
GREY = colors.HexColor('#575757')
styles = getSampleStyleSheet()
styles.add(ParagraphStyle(name='GuideTitle', fontName='Helvetica-Bold', fontSize=30, leading=34, spaceAfter=16, textColor=INK))
styles.add(ParagraphStyle(name='Chapter', fontName='Helvetica-Bold', fontSize=23, leading=27, spaceAfter=15, textColor=INK))
styles.add(ParagraphStyle(name='Subhead', fontName='Helvetica-Bold', fontSize=11.5, leading=15, spaceBefore=11, spaceAfter=5, textColor=INK))
styles.add(ParagraphStyle(name='GuideBody', fontName='Helvetica', fontSize=10, leading=14, spaceAfter=8, textColor=INK))
styles.add(ParagraphStyle(name='GuideBullet', parent=styles['GuideBody'], leftIndent=11, firstLineIndent=-9, spaceAfter=5))
styles.add(ParagraphStyle(name='Reference', parent=styles['GuideBody'], fontSize=8.8, leading=12, wordWrap='CJK', spaceAfter=12))
styles.add(ParagraphStyle(name='CodeBlock', fontName='Courier', fontSize=8.3, leading=11, backColor=colors.HexColor('#F2F2EF'), borderPadding=10, spaceBefore=6, spaceAfter=9))


class Process(Flowable):
    def __init__(self, stages):
        Flowable.__init__(self)
        self.stages = stages
        self.width, self.height = 170 * mm, 30 * mm

    def draw(self):
        canvas = self.canv
        gap = 10
        width = (self.width - gap * (len(self.stages) - 1)) / len(self.stages)
        for index, stage in enumerate(self.stages):
            left = index * (width + gap)
            canvas.setStrokeColor(INK)
            canvas.setFillColor(colors.HexColor('#F5F4EF'))
            canvas.roundRect(left, 10, width, 62, 3, fill=1, stroke=1)
            text = Paragraph(html.escape(stage), ParagraphStyle(name='Node', fontName='Helvetica', fontSize=9, leading=12, alignment=TA_LEFT))
            _, height = text.wrap(width - 14, 48)
            text.drawOn(canvas, left + 7, 10 + (62 - height) / 2)
            if index < len(self.stages) - 1:
                canvas.setStrokeColor(RED)
                canvas.line(left + width, 41, left + width + gap, 41)
                canvas.line(left + width + gap - 3, 44, left + width + gap, 41)
                canvas.line(left + width + gap - 3, 38, left + width + gap, 41)


def decorate(canvas, document):
    canvas.saveState()
    width, height = document.pagesize
    canvas.setFont('Helvetica-Bold', 8)
    canvas.setFillColor(RED)
    canvas.drawString(20 * mm, height - 16 * mm, 'QUICKSLOT / LEARNING NOTES')
    canvas.setFillColor(GREY)
    canvas.setFont('Helvetica', 8)
    canvas.drawString(20 * mm, 13 * mm, 'Java to Spring Boot | 20 October 2026')
    canvas.drawRightString(width - 20 * mm, 13 * mm, str(document.page))
    canvas.restoreState()


def inline(text):
    safe = html.escape(text)
    match = re.search(r'https://\S+', safe)
    if match:
        url = match.group(0)
        safe = safe[:match.start()] + f'<link href="{url}" color="#AF2029">{url}</link>' + safe[match.end():]
    return safe


def build():
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    story = []
    code = None
    for line in SOURCE.read_text(encoding='utf-8-sig').splitlines():
        if line.startswith('```'):
            if code is None:
                code = []
            else:
                story.append(Preformatted('\n'.join(code), styles['CodeBlock']))
                code = None
            continue
        if code is not None:
            code.append(line)
        elif line.startswith('# '):
            story.append(Spacer(1, 18 * mm))
            story.append(Paragraph(inline(line[2:]), styles['GuideTitle']))
        elif line.startswith('## '):
            story.append(PageBreak())
            story.append(Paragraph(inline(line[3:]), styles['Chapter']))
        elif line.startswith('### '):
            story.append(Paragraph(inline(line[4:]), styles['Subhead']))
        elif line.startswith('[flow] '):
            story.append(Process([part.strip() for part in line[7:].split('|')]))
        elif line.startswith('- '):
            story.append(Paragraph('&#8226; ' + inline(line[2:]), styles['GuideBullet']))
        elif line.strip():
            style = styles['Reference'] if 'https://' in line else styles['GuideBody']
            story.append(Paragraph(inline(line), style))
    document = SimpleDocTemplate(str(OUTPUT), pagesize=(210 * mm, 297 * mm), leftMargin=20 * mm, rightMargin=20 * mm, topMargin=27 * mm, bottomMargin=23 * mm, title='Learn Spring Boot through QuickSlot', author='QuickSlot project')
    document.build(story, onFirstPage=decorate, onLaterPages=decorate)
    print(OUTPUT)


if __name__ == '__main__':
    build()
