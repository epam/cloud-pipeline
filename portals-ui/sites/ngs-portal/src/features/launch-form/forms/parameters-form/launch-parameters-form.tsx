import type { MappedPipelineParameter } from '@cloud-pipeline/core';
import LaunchParameter from './launch-parameter';
import { useMemo, useRef, useState } from 'react';
import { Divider } from 'antd';
import classNames from 'classnames';
import './style.css';

type Props = {
  parameters?: MappedPipelineParameter[];
  onChange: (key: string, parameter: MappedPipelineParameter) => void;
  prettyNameEditable?: boolean;
  readOnly?: boolean;
};

const OTHER_SECTION_NAME = 'other';

export function LaunchParametersForm({
  parameters,
  onChange,
  prettyNameEditable,
  readOnly,
}: Props) {
  const [highlightedSection, setHighlightedSection] = useState('');
  const highlightTimeoutRef = useRef<ReturnType<typeof setTimeout> | undefined>(
    undefined,
  );
  const sectionRefs = useRef<Record<string, HTMLDivElement | null>>({});
  const sections = useMemo(() => {
    if (parameters?.length) {
      const sections = {} as Record<string, MappedPipelineParameter[]>;
      parameters.forEach((parameter) => {
        if (!parameter.isSystemParameter) {
          const sectionName = (
            parameter.section ?? OTHER_SECTION_NAME
          ).toLowerCase();
          if (!sections[sectionName]) {
            sections[sectionName] = [];
          }
          sections[sectionName].push(parameter);
        }
      });
      return Object.values(sections);
    }
    return [];
  }, [parameters]);
  if (!parameters) {
    return <div>No data</div>;
  }
  const scrollToSection = (section: string) => () => {
    const sectionRef = sectionRefs.current[section];
    if (sectionRef) {
      clearTimeout(highlightTimeoutRef.current);
      sectionRef.scrollIntoView({ behavior: 'smooth' });
      setHighlightedSection(section);
      highlightTimeoutRef.current = setTimeout(() => {
        setHighlightedSection('');
      }, 2000);
    }
  };
  return (
    <div className="flex flex-nowrap gap-4 overflow-auto w-full">
      {sections.length > 1 ? (
        <div className="border-r pr-4 min-w-fit flex flex-col sticky top-0">
          {sections.map((section) => {
            const sectionName = (
              section[0].section || OTHER_SECTION_NAME
            ).toLowerCase();
            return (
              <a
                key={sectionName}
                className="capitalize"
                onClick={scrollToSection(sectionName)}>
                {sectionName}
              </a>
            );
          })}
        </div>
      ) : null}
      <div className="flex flex-col w-full">
        {sections.map((section) => {
          const sectionName = (
            section[0]?.section ?? OTHER_SECTION_NAME
          ).toLowerCase();
          return (
            <div
              key={sectionName}
              ref={(node) => {
                sectionRefs.current[sectionName] = node;
              }}
              className="flex flex-col gap-3">
              {sections.length > 1 ? (
                <Divider
                  variant="dashed"
                  dashed
                  className={classNames('text capitalize parameter-section', {
                    highlighted: highlightedSection === sectionName,
                  })}>
                  {sectionName}
                </Divider>
              ) : null}
              {section.map((parameter) => (
                <LaunchParameter
                  key={`parameter_${parameter.initialKey}`}
                  parameter={parameter}
                  onChange={onChange}
                  prettyNameEditable={prettyNameEditable}
                  readOnly={readOnly}
                />
              ))}
            </div>
          );
        })}
      </div>
    </div>
  );
}
