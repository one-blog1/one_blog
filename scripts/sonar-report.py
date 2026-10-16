#!/usr/bin/env python3
"""SonarQube 서버의 품질 게이트 조건, 새 코드의 열린 이슈, 검토 전 핫스팟을 실행 화면 Annotations에 띄운다.

서버 화면을 열지 않아도(또는 서버에 닿지 않는 곳에서도) 게이트가 왜 실패했는지 GitHub에서 보이게 하기 위해서다.
보고만 하고 실패시키지 않는다(늘 0으로 끝남).

쓰는 법: SONAR_HOST_URL, SONAR_TOKEN 환경 변수를 두고 ./scripts/sonar-report.py [프로젝트 키(기본 one-blog)]
"""
import base64
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

PROJECT = sys.argv[1] if len(sys.argv) > 1 else "one-blog"
HOST = os.environ.get("SONAR_HOST_URL", "").rstrip("/")
TOKEN = os.environ.get("SONAR_TOKEN", "")
MAX_LINES = 50          # 한 annotation에 적는 최대 줄 수
MAX_FILE_NOTES = 10     # 파일 위치에 따로 띄우는 이슈 수 (GitHub는 단계마다 annotation 수를 제한함)


def escape(text):
    """annotation 메시지에 넣을 수 있게 바꾼다 (줄바꿈은 %0A)."""
    return text.replace("%", "%25").replace("\r", "").replace("\n", "%0A")


def note(level, title, message, file=None, line=None):
    where = f" file={file},line={line}," if file else " "
    print(f"::{level}{where}title={title}::{escape(message)[:6000]}")


def get(path, **params):
    url = f"{HOST}{path}?{urllib.parse.urlencode(params)}"
    request = urllib.request.Request(url)
    if TOKEN:
        auth = base64.b64encode(f"{TOKEN}:".encode()).decode()
        request.add_header("Authorization", f"Basic {auth}")
    with urllib.request.urlopen(request, timeout=20) as response:
        return json.load(response)


def path_of(component):
    """'one-blog:src/main/...' → 'src/main/...'"""
    return component.split(":", 1)[1] if ":" in component else component


def report_gate():
    status = get("/api/qualitygates/project_status", projectKey=PROJECT)["projectStatus"]
    lines = []
    for condition in status.get("conditions", []):
        mark = "실패" if condition["status"] == "ERROR" else "통과"
        lines.append(f"[{mark}] {condition['metricKey']}: {condition.get('actualValue', '-')}"
                     f" (기준 {condition['comparator']} {condition.get('errorThreshold', '-')})")
    period = status.get("period", {})
    lines.append(f"새 코드 기준: {period.get('mode', '-')} {period.get('parameter', '')} ({period.get('date', '-')})")
    level = "error" if status["status"] == "ERROR" else "notice"
    note(level, f"품질 게이트 {status['status']}", "\n".join(lines))


def report_issues():
    data = get("/api/issues/search", componentKeys=PROJECT, inNewCodePeriod="true", resolved="false",
               ps=100, s="SEVERITY", asc="false")
    issues = data.get("issues", [])
    if not issues:
        note("notice", "새 코드 이슈 0개", "새 코드에 열린 이슈가 없습니다")
        return
    lines = [f"{i['rule']} {i.get('severity', '')} {path_of(i['component'])}:{i.get('line', '-')} {i['message']}"
             for i in issues[:MAX_LINES]]
    if data.get("total", len(issues)) > MAX_LINES:
        lines.append(f"... 모두 {data['total']}개 (서버에서 나머지를 확인)")
    note("error", f"새 코드 이슈 {data.get('total', len(issues))}개", "\n".join(lines))
    for issue in issues[:MAX_FILE_NOTES]:
        note("warning", issue["rule"], issue["message"], path_of(issue["component"]), issue.get("line", 1))


def report_hotspots():
    data = get("/api/hotspots/search", projectKey=PROJECT, inNewCodePeriod="true", status="TO_REVIEW", ps=100)
    hotspots = data.get("hotspots", [])
    total = data.get("paging", {}).get("total", len(hotspots))
    if not hotspots:
        note("notice", "새 코드 검토 전 핫스팟 0개", "새 코드에 검토 전 핫스팟이 없습니다")
        return
    lines = [f"{h.get('ruleKey', '')} {path_of(h['component'])}:{h.get('line', '-')} {h['message']}"
             for h in hotspots[:MAX_LINES]]
    note("warning", f"새 코드 검토 전 핫스팟 {total}개",
         "\n".join(lines) + "\n서버 Security Hotspots 탭에서 Safe·Fixed로 표시해 주세요")


def main():
    if not HOST:
        note("warning", "SonarQube 보고", "SONAR_HOST_URL이 없어 보고를 건너뜁니다")
        return
    for part in (report_gate, report_issues, report_hotspots):
        try:
            part()
        except urllib.error.HTTPError as e:
            note("warning", "SonarQube 보고", f"{part.__name__}: 서버가 {e.code}로 거절했습니다"
                 " (토큰 권한 확인: 이 프로젝트 Browse 권한이 필요)")
        except (urllib.error.URLError, OSError, KeyError, ValueError) as e:
            note("warning", "SonarQube 보고", f"{part.__name__}: {e}")


if __name__ == "__main__":
    main()
