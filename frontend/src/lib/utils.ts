import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

/** Joins class names and resolves Tailwind conflicts, so a caller's class wins over a component's default. */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}
