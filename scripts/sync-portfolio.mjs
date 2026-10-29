import fs from 'fs';
import path from 'path';

const projectJsonPath = process.argv[2] || 'portfolio.json';
const gitlabDataPath = process.argv[3];

if (!gitlabDataPath || !fs.existsSync(gitlabDataPath)) {
  console.error(`Error: No se encontró el archivo destino en: ${gitlabDataPath}`);
  process.exit(1);
}

const newProject = JSON.parse(fs.readFileSync(projectJsonPath, 'utf-8'));
let targetFileContent = fs.readFileSync(gitlabDataPath, 'utf-8');

const herramientasFormatted = (newProject.herramientas || [])
  .map(tool => `   iconosNombres['${tool}']`)
  .join(',\n');

const jsCodeBlock = `
 // ${newProject.id}
 ${newProject.id}: {
  id: '${newProject.id}',
  titulo: '${newProject.titulo}',
  tituloCorto: '${newProject.tituloCorto}',
  subtitulo: '${newProject.subtitulo}',
  categoria: '${newProject.categoria}',
  destacado: ${newProject.destacado},
  confidencial: ${newProject.confidencial},
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
  galeria: ${JSON.stringify(newProject.galeria)},
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
  portafolioSlider: ${newProject.portafolioSlider},
  img: '${newProject.img}',
  repositorio: '${newProject.repositorio}',
  descripcionExtra: '${newProject.descripcionExtra || ''}',
 },
`;

const regexProject = new RegExp(`\\/\\/\\s*${newProject.id}[\\s\\S]*?${newProject.id}:\\s*\\{[\\s\\S]*?\\n \\},`, 'g');

if (regexProject.test(targetFileContent)) {
  targetFileContent = targetFileContent.replace(regexProject, jsCodeBlock.trim());
  console.log(`Proyecto '${newProject.id}' actualizado.`);
} else {
  const insertAnchor = 'export const portFolioData = {';
  targetFileContent = targetFileContent.replace(insertAnchor, `${insertAnchor}\n${jsCodeBlock}`);
  console.log(`Proyecto '${newProject.id}' insertado al inicio.`);
}

fs.writeFileSync(gitlabDataPath, targetFileContent, 'utf-8');

