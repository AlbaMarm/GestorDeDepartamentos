package creacionFichero;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import org.iesalandalus.programacion.utilidades.Entrada;

public class GestionDep {

    static final int LONGITUD_REGISTRO = 64;
    static final int LONGITUD_TEXTO = 15;

    // Desplazamientos: nombre, localidad y núm. Empleados
    static final int DES_NOMBRE = 2;
    static final int DES_LOCALIDAD = 32;
    static final int DES_NUM_EMP = 62;

    // guiones - para texto no usado y asteriscos * para borrado
    static final String TXT_SIN_USO = "-".repeat(LONGITUD_TEXTO);
    static final String TXT_BORRADO = "*".repeat(LONGITUD_TEXTO);

    // estado de posiciones del fichero aleatorio
    enum Estado { FUERA_FICHERO, NUNCA_UTILIZADA, ELIMINADA, ACTIVA, ANOMALA}


    // fichero creado en el directorio seleccionado
    static final File f = new File("departamentos/AleatorioDepart.dat");

    public static void main(String[] args){
        prepararFichero();
        System.out.println("INSERTAR DEPARTAMENTO");

        System.out.print("Número de departamento: ");
        int numDep = Entrada.entero();

        String nombre = pedirTexto("Nombre: ");

        String localidad = pedirTexto("Localidad: ");

        System.out.print("Número de empleados: ");
        int numEmp = Entrada.entero();

        try {
            insertaDepartamento(numDep, nombre, localidad, numEmp);
            consultaDepartamento(numDep);
        } catch (IllegalArgumentException | IllegalStateException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    public static long posicionRegistro(int numDep){
        return (long) (numDep - 1) * LONGITUD_REGISTRO;
    }

    private static void prepararFichero(){
        try {
            f.getParentFile().mkdirs();
            if (f.createNewFile()) {
                System.out.println("Fichero creado.");
            } else {
                System.out.println("Fichero existente, se conserva.");
            }
        } catch (IOException e){
            System.out.println("No se ha podido crear el fichero.");
        }
    }


    // validamos texto PARA NO TRUNCAR EN SILENCIO (si el texto pasa de 15, se rechaza y se pide de nuevo)
    private static String validarTexto(String texto){
        if(texto == null) {
            return null;
        }
        texto = texto.strip();

        // no texto vacío o muy largo
        if(texto.isEmpty() || texto.length() > LONGITUD_TEXTO){
            return null;
        }

        return texto;
    }

    // método de escribir texto en posición del puntero (15 caracteres)
    private static void escribirTexto(RandomAccessFile file, String texto) throws IOException{
        // 1 rellenar espacios a la derecha 15 --> usar String.format
        texto = String.format("%-" + LONGITUD_TEXTO + "s", texto);
        // .format("%-15s", texto) para completar con espacios si es más corto

        // escribimos fichero
        file.writeChars(texto);

    }

    // método de leer 15 caracteres desde pos actual y devolver texto sin relleno
    private static String leerTexto(RandomAccessFile file) throws IOException {
        // 1 Creamos un StringBuilder
        StringBuilder texto = new StringBuilder();

        // 2 Repetimos file.readChar() 15 veces y añadimos
        for (int i = 0; i < LONGITUD_TEXTO; i++){
            texto.append(file.readChar());
        }

        // 3 Devolvemos resultado y eliminamos espacios de relleno final
        return texto.toString().stripTrailing(); // no .trim() porque queremos eliminar SOLO espacios del final no todos
    }

    // pedir texto por pantalla
    private static String pedirTexto(String mensaje){
        String texto;

        do{
            System.out.print(mensaje);
            texto = Entrada.cadena();

            texto = validarTexto(texto);

            if (texto == null) {
                System.out.println(
                        "Error: el texto debe tener entre 1 y " + LONGITUD_TEXTO + " caracteres. Inténtalo de nuevo."
                );
            }

        }while (texto == null);

        return texto;
    }

    public static void escribirRegistroVacio(RandomAccessFile file) throws IOException{
        file.writeShort(0);
        escribirTexto(file, TXT_SIN_USO);
        escribirTexto(file, TXT_SIN_USO);
        file.writeShort(0);
    }

    private static Estado estadoPosicion(RandomAccessFile file, int numDep) throws IOException{
        long pos = posicionRegistro(numDep);
        if(pos >= file.length()){
            return Estado.FUERA_FICHERO;
        }

        //Esto solo vale para los registros activos
        //if(pos/LONGITUD_REGISTRO != file.readChar())

        if(pos + LONGITUD_REGISTRO > file.length()){
            return  Estado.ANOMALA;
        }

        //comienzo registro y sus campos
        file.seek(pos);
        short id = file.readShort();
        String nombre = leerTexto(file);
        String localidad = leerTexto(file);
        short numEmp = file.readShort();

        if (id == 0 && nombre.equals(TXT_SIN_USO) && localidad.equals(TXT_SIN_USO) && numEmp == 0) {
            return Estado.NUNCA_UTILIZADA;
        }

        if (id == -1 && nombre.equals(TXT_BORRADO) && localidad.equals(TXT_BORRADO) && numEmp == -1) {
            return Estado.ELIMINADA;
        }

        if (id == numDep && numEmp >= 0 && !nombre.isEmpty() && !localidad.isEmpty()) {
            return Estado.ACTIVA;
        }


        return Estado.ANOMALA;
    }

    private static boolean numDepValido(int numDep){
        return numDep > 0 && numDep <= 32767;
    }

    private static boolean numEmpValido(int numEmp){
        return numEmp >= 0 && numEmp <= 32767;
    }

    // ======== DEPARTAMENTOS CREACIÓN LECTURA ELIMINACIÓN... =========

    public static boolean existeDepartamento(int numDep){

        if(!numDepValido(numDep)){
            throw new IllegalArgumentException("El número de departamento debe estar entre 1 y 32767.");
        }

        RandomAccessFile file = null;
        try {
            file = new RandomAccessFile(f, "r");
            return estadoPosicion(file, numDep) == Estado.ACTIVA;
        } catch (FileNotFoundException e) {
            throw new IllegalStateException("No se ha encontrado el fichero " + f.getName() + ".", e);
        } catch (IOException e) {
            throw new IllegalStateException("Error de lectura en el fichero " + f.getName() + ".", e);
        } finally {
            if (file != null) {
                try {
                    file.close();
                } catch (IOException e) {
                    System.out.println("No se ha podido cerrar el fichero.");
                }
            }
        }
    }

    public static void insertaDepartamento(int numDep, String nombre, String localidad, int numEmp){

        // validaciones
        if (!numDepValido(numDep) || !numEmpValido(numEmp)) {
            throw new IllegalArgumentException(
                    "El número de departamento o de empleados no es válido."
            );
        }
        nombre = validarTexto(nombre);
        localidad = validarTexto(localidad);
        if (nombre == null || localidad == null) {
            throw new IllegalArgumentException(
                    "El nombre y la localidad deben tener entre 1 y 15 caracteres."
            );
        }

        RandomAccessFile file = null;
        try {
            file = new RandomAccessFile(f, "rw");

            // validamos fichero físico
            if (file.length() % LONGITUD_REGISTRO != 0) {
                throw new IllegalStateException(
                        "El fichero contiene un registro incompleto."
                );
            }


            // estados y opciones
            Estado estado = estadoPosicion(file, numDep);
            if (estado == Estado.ACTIVA){
                System.out.println("El departamento " + numDep + " ya existe.");
                return;
            }
            if (estado == Estado.ANOMALA) {
                throw new IllegalStateException("La posición del departamento " + numDep + " contiene datos anómalos.");
            }
            // rellenamos huecos (solo si FUERA_FICHERO):
            if (estado == Estado.FUERA_FICHERO) {
                file.seek(file.length());

                while (file.getFilePointer() < posicionRegistro(numDep)) {
                    escribirRegistroVacio(file);
                }
            }

            file.seek(posicionRegistro(numDep));
            file.writeShort(numDep);
            escribirTexto(file, nombre);
            escribirTexto(file, localidad);
            file.writeShort(numEmp);

            System.out.println("Departamento " + numDep + " insertado.");

        } catch (FileNotFoundException e) {
            throw new IllegalStateException("No se ha encontrado el fichero " + f.getName() + ".", e);
        } catch (IOException e) {
            throw new IllegalStateException("Error de acceso al fichero " + f.getName() + ".", e);
        } finally {
            if (file != null) {
                try {
                    file.close();
                } catch (IOException e) {
                    System.out.println("No se ha podido cerrar el fichero.");
                }
            }
        }
    }

    public static void consultaDepartamento(int numDep){

        if(!numDepValido(numDep)){
            throw new IllegalArgumentException("El número de departamento debe estar entre 1 y 32767.");
        }

        RandomAccessFile file = null;
        try {
            file = new RandomAccessFile(f, "r");
            // Comprobamos si el departamento está activo
            Estado estado = estadoPosicion(file, numDep);

            if (estado != Estado.ACTIVA) {
                System.out.println("El departamento no existe.");
                return;
            }

            // leemos los datos del departamento y los msotramos
            file.seek(posicionRegistro(numDep));

            short id = file.readShort();
            String nombre = leerTexto(file);
            String localidad = leerTexto(file);
            short numEmp = file.readShort();
            System.out.println("Departamento con id: " + id + ", con nombre: " + nombre + ", localidad: " + localidad + " y número de empleados: " + numEmp);

        } catch (FileNotFoundException e) {
            throw new IllegalStateException("No se ha encontrado el fichero " + f.getName() + ".", e);
        } catch (IOException e) {
            throw new IllegalStateException("Error de acceso al fichero " + f.getName() + ".", e);
        } finally {
            if (file != null) {
                try {
                    file.close();
                } catch (IOException e) {
                    System.out.println("No se ha podido cerrar el fichero.");
                }
            }
        }
    }

    public static void actualizaNumeroEmpleados(int numDep, int nuevoNumEmp){

        if(!numDepValido(numDep)){
            throw new IllegalArgumentException("El número de departamento debe estar entre 1 y 32767.");
        }
        if(!numEmpValido(nuevoNumEmp)){
            throw new IllegalArgumentException("El número de empleados debe estar entre 0 y 32767.");
        }

        RandomAccessFile file = null;
        try {
            file = new RandomAccessFile(f, "rw");
            // Comprobamos si el departamento está activo
            Estado estado = estadoPosicion(file, numDep);

            if (estado != Estado.ACTIVA) {
                System.out.println("El departamento no existe.");
                return;
            }

            // leemos los datos del departamento y actualizamos
            file.seek(posicionRegistro(numDep) + DES_NUM_EMP);

            file.writeShort(nuevoNumEmp);
            System.out.println("Nuevo número de empleados: " + nuevoNumEmp);

        } catch (FileNotFoundException e) {
            throw new IllegalStateException("No se ha encontrado el fichero " + f.getName() + ".", e);
        } catch (IOException e) {
            throw new IllegalStateException("Error de acceso al fichero " + f.getName() + ".", e);
        } finally {
            if (file != null) {
                try {
                    file.close();
                } catch (IOException e) {
                    System.out.println("No se ha podido cerrar el fichero.");
                }
            }
        }
    }

    public static void actualizaNombreLocalidad(int numDep, String nombre, String localidad){

        if(!numDepValido(numDep)){
            throw new IllegalArgumentException("El número de departamento debe estar entre 1 y 32767.");
        }
        nombre = validarTexto(nombre);
        localidad = validarTexto(localidad);
        if (nombre == null || localidad == null) {
            throw new IllegalArgumentException(
                    "El nombre y la localidad deben tener entre 1 y 15 caracteres."
            );
        }

        RandomAccessFile file = null;
        try {
            file = new RandomAccessFile(f, "rw");
            // Comprobamos si el departamento está activo
            Estado estado = estadoPosicion(file, numDep);

            if (estado != Estado.ACTIVA) {
                System.out.println("El departamento no existe.");
                return;
            }

            // leemos y escribimos
            file.seek(posicionRegistro(numDep) + DES_NOMBRE);
            escribirTexto(file, nombre);
            file.seek(posicionRegistro(numDep) + DES_LOCALIDAD);
            escribirTexto(file, localidad);
            System.out.println("Nuevo nombre: " + nombre + " y nueva localidad: "+ localidad);

        } catch (FileNotFoundException e) {
            throw new IllegalStateException("No se ha encontrado el fichero " + f.getName() + ".", e);
        } catch (IOException e) {
            throw new IllegalStateException("Error de acceso al fichero " + f.getName() + ".", e);
        } finally {
            if (file != null) {
                try {
                    file.close();
                } catch (IOException e) {
                    System.out.println("No se ha podido cerrar el fichero.");
                }
            }
        }
    }

    public static void eliminaDepartamento(int numDep){

        if(!numDepValido(numDep)){
            throw new IllegalArgumentException("El número de departamento debe estar entre 1 y 32767.");
        }

        RandomAccessFile file = null;
        try {
            file = new RandomAccessFile(f, "rw");
            // Comprobamos si el departamento está activo
            Estado estado = estadoPosicion(file, numDep);

            if (estado != Estado.ACTIVA) {
                System.out.println("El departamento no existe.");
                return;
            }

            // leemos y escribimos
            file.seek(posicionRegistro(numDep));

            file.writeShort(-1);
            escribirTexto(file, TXT_BORRADO);
            escribirTexto(file, TXT_BORRADO);
            file.writeShort(-1);

            System.out.println("Departamento con nº de departamento: " + numDep + " borrado.");

        } catch (FileNotFoundException e) {
            throw new IllegalStateException("No se ha encontrado el fichero " + f.getName() + ".", e);
        } catch (IOException e) {
            throw new IllegalStateException("Error de acceso al fichero " + f.getName() + ".", e);
        } finally {
            if (file != null) {
                try {
                    file.close();
                } catch (IOException e) {
                    System.out.println("No se ha podido cerrar el fichero.");
                }
            }
        }
    }



}
