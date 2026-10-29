import fs from 'fs';
import path from 'path';

const projectJsonPath = process.argv[2] || 'portfolio.json';
const gitlabDataPath = process.argv[3];
const assetsDir = 'docs/portfolio';

if (!gitlabDataPath || !fs.existsSync(gitlabDataPath)) {
  console.error(
    `Error: No se encontró el archivo destino en: ${gitlabDataPath}`,
  );
  process.exit(1);
}

const newProject = JSON.parse(fs.readFileSync(projectJsonPath, 'utf-8'));
let targetFileContent = fs.readFileSync(gitlabDataPath, 'utf-8');

// Detección automática de portada y galería desde la carpeta
let coverImage = newProject.img || '';
let galleryImages = [];

if (fs.existsSync(assetsDir)) {
  const files = fs
    .readdirSync(assetsDir)
    .filter((f) => /\.(jpe?g|png|webp|svg)$/i.test(f));

  // Buscar archivo de portada por convención
  const coverMatch =
    files.find((f) => /^(cover|portada|thumbnail)/i.test(f)) ||
    files.find((f) => f.toLowerCase().includes(newProject.id.toLowerCase())) ||
    files[0];

  if (coverMatch) {
    coverImage = coverMatch;
    // La galería contendrá primero la portada y luego las demás capturas ordenadas
    galleryImages = [
      coverMatch,
      ...files.filter((f) => f !== coverMatch).sort(),
    ];
  }
}

// Si se especificó manualmente en portfolio.json, se respeta; sino, usa los detectados
const finalImg = newProject.img || coverImage || `${newProject.id}.jpg`;
const finalGallery =
  newProject.galeria && newProject.galeria.length > 0
    ? newProject.galeria
    : galleryImages.length > 0
      ? galleryImages
      : [finalImg];

// Diccionario de claves existentes en variables.js
const validIconos = [
  'html',
  'css',
  'javascript',
  'typescript',
  'tailwind',
  'reactRouter',
  'api',
  'zustand',
  'zod',
  'localstorage',
  'react',
  'reactNative',
  'axios',
  'reacthooks',
  'usememo',
  'figma',
  'aframe',
  'android',
  'kotlin',
];

const herramientasFormatted = (newProject.herramientas || [])
  .map((tool) => {
    if (validIconos.includes(tool.toLowerCase())) {
      return `   iconosNombres['${tool.toLowerCase()}']`;
    }
    return `   '${tool.charAt(0).toUpperCase() + tool.slice(1)}'`;
  })
  .join(',\n');

const jsCodeBlock = `
 // ${newProject.id}
 ${newProject.id}: {
  id: '${newProject.id}',
  titulo: '${newProject.titulo}',
  tituloCorto: '${newProject.tituloCorto}',
  subtitulo: '${newProject.subtitulo}',
  categoria: '${newProject.categoria}',
  destacado: ${Boolean(newProject.destacado)},
  confidencial: ${Boolean(newProject.confidencial)},
  rol: '${newProject.rol}',
  cliente: '${newProject.cliente}',
  ano: '${newProject.ano}',
  resumen:
   '${newProject.resumen}',
  casoEstudio: {
   problema:
    '${newProject.casoEstudio.problema}',
   solucion:
    '${newProject.casoEstudio.solucion}',
   impacto: ${JSON.stringify(newProject.casoEstudio.impacto, null, 4).replace(/\n/g, '\n   ')},
  },
  enlaces: ${JSON.stringify(newProject.enlaces, null, 3).replace(/\n/g, '\n  ')},
  galeria: ${JSON.stringify(finalGallery)},
  herramientas: [
${herramientasFormatted}
  ],
  descripcion:
   '${newProject.descripcion}',
  fullDescripcion:
   '${newProject.fullDescripcion}',
  cargo: '${newProject.cargo}',
  enlace: '${newProject.enlace}',
  itemmejoresproyectos: '${newProject.itemmejoresproyectos}',
  portafolioSlider: ${Boolean(newProject.portafolioSlider)},
  img: '${finalImg}',
  repositorio: '${newProject.repositorio}',
  descripcionExtra: '${newProject.descripcionExtra || ''}',
 },
`;

const regexProject = new RegExp(
  `\\/\\/\\s*${newProject.id}[\\s\\S]*?${newProject.id}:\\s*\\{[\\s\\S]*?\\n \\},`,
  'g',
);

if (regexProject.test(targetFileContent)) {
  targetFileContent = targetFileContent.replace(
    regexProject,
    jsCodeBlock.trim(),
  );
  console.log(
    `Proyecto '${newProject.id}' actualizado con ${finalGallery.length} imágenes.`,
  );
} else {
  const insertAnchor = 'export const portFolioData = {';
  targetFileContent = targetFileContent.replace(
    insertAnchor,
    `${insertAnchor}\n${jsCodeBlock}`,
  );
  console.log(
    `Proyecto '${newProject.id}' insertado con ${finalGallery.length} imágenes.`,
  );
}

fs.writeFileSync(gitlabDataPath, targetFileContent, 'utf-8');
