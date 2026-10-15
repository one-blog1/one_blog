#!/usr/bin/env python3
"""실패한 테스트·컴파일 오류를 GitHub Actions 실행 화면의 Annotations에 띄운다 (CI 실패 단계에서 쓴다).

쓰는 법: python3 scripts/ci-test-annotation.py <테스트 결과 폴더> <gradle 로그 파일>
  예) python3 scripts/ci-test-annotation.py build/test-results/unitTest gradle-unit.log
테스트 결과(XML)에 실패가 있으면 최대 10개를 띄우고, 없으면(컴파일 오류 등) gradle 로그에서 오류 줄을 띄운다.
"""
import glob
import os
import sys
import xml.etree.ElementTree as ET


def esc(text):
    return text.replace('%', '%25').replace('\r', '').replace('\n', '%0A')


def esc_title(text):
    return esc(text).replace(':', '%3A').replace(',', '%2C')


def main():
    results_dir = sys.argv[1] if len(sys.argv) > 1 else 'build/test-results/test'
    log_file = sys.argv[2] if len(sys.argv) > 2 else 'gradle-test.log'
    shown = 0
    for f in sorted(glob.glob(os.path.join(results_dir, 'TEST-*.xml'))):
        for case in ET.parse(f).getroot().iter('testcase'):
            for bad in list(case.iter('failure')) + list(case.iter('error')):
                if shown < 10:
                    text = (bad.get('message') or '') + ' | ' + (bad.text or '')
                    title = case.get('classname', '').split('.')[-1] + '.' + case.get('name', '')
                    print('::error title=' + esc_title(title) + '::' + esc(text)[:1500])
                    shown += 1
    if shown == 0 and os.path.exists(log_file):
        with open(log_file, encoding='utf-8', errors='replace') as log:
            lines = [l.strip() for l in log if 'error:' in l or 'FAILURE' in l or 'What went wrong' in l]
        print('::error title=gradle::' + '%0A'.join(l.replace('%', '%25') for l in lines[:15])[:3000])


if __name__ == '__main__':
    main()
