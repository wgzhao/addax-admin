export const hexToRgb = (hex) => {
  // Expand shorthand form (e.g. "03F") to full form (e.g. "0033FF")
  const shorthandRegex = /^#?([a-f\d])([a-f\d])([a-f\d])$/i

  hex = hex.replace(shorthandRegex, (m: string, r: string, g: string, b: string) => {
    return r + r + g + g + b + b
  })

  const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex)

  return result
    ? `${parseInt(result[1], 16)},${parseInt(result[2], 16)},${parseInt(result[3], 16)}`
    : null
}

export const createSort = (sortBy) => {
  if (sortBy.length > 0) {
    return { sortField: sortBy[0]['key'], sortOrder: sortBy[0]['order'] }
  } else {
    return { sortField: null, sortOrder: null }
  }
}

export const injectDialogKey = Symbol()

// 导出常量
export * from './constants'
