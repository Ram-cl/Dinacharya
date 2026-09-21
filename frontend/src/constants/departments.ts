export const DEPARTMENTS = [
  'ASE',
  'Business Development',
  'Cybersecurity',
  'Dev',
  'DevOps',
  'Engineering',
  'UI',
] as const;

export type DepartmentName = (typeof DEPARTMENTS)[number];

export const DEFAULT_DEPARTMENT: DepartmentName = 'Engineering';
