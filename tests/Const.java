import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/**
 * Marks a static final array field as constant data.
 *
 * On a standard JVM this annotation is ignored — the array is
 * heap-allocated via <clinit> as usual.  pjvmpack.py (and picojc)
 * act on it: the array data is emitted in the .pjvm const_data
 * section, read-only, accessed through PROG() / the pager.
 *
 * This eliminates both the <clinit> initialization bytecodes and
 * the heap allocation.  On paged targets, constant data is served
 * from disk on demand — an 8085 with 2KB RAM can address 4GB of
 * constant tables.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.FIELD)
public @interface Const {
}
