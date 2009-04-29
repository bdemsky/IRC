public class List {

private class list_node {
    void* dataPtr;
    struct list_node* nextPtr;
}

typedef list_node_t* list_iter_t;

    list_node_t head;
    long (*compare)(const void*, const void*);   /* returns {-1,0,1}, 0 -> equal */
    long size;
} list_t;


/* =============================================================================
 * list_iter_reset
 * =============================================================================
 */
void
list_iter_reset (list_iter_t* itPtr, list_t* listPtr);


/* =============================================================================
 * TMlist_iter_reset
 * =============================================================================
 */
TM_CALLABLE
void
TMlist_iter_reset (TM_ARGDECL  list_iter_t* itPtr, list_t* listPtr);


/* =============================================================================
 * list_iter_hasNext
 * =============================================================================
 */
bool_t
list_iter_hasNext (list_iter_t* itPtr, list_t* listPtr);


/* =============================================================================
 * TMlist_iter_hasNext
 * =============================================================================
 */
TM_CALLABLE
bool_t
TMlist_iter_hasNext (TM_ARGDECL  list_iter_t* itPtr, list_t* listPtr);


/* =============================================================================
 * list_iter_next
 * =============================================================================
 */
void*
list_iter_next (list_iter_t* itPtr, list_t* listPtr);


/* =============================================================================
 * TMlist_iter_next
 * =============================================================================
 */
TM_CALLABLE
void*
TMlist_iter_next (TM_ARGDECL  list_iter_t* itPtr, list_t* listPtr);


/* =============================================================================
 * list_alloc
 * -- If NULL passed for 'compare' function, will compare data pointer addresses
 * -- Returns NULL on failure
 * =============================================================================
 */
list_t*
list_alloc (long (*compare)(const void*, const void*));


/* =============================================================================
 * Plist_alloc
 * -- If NULL passed for 'compare' function, will compare data pointer addresses
 * -- Returns NULL on failure
 * =============================================================================
 */
list_t*
Plist_alloc (long (*compare)(const void*, const void*));


/* =============================================================================
 * TMlist_alloc
 * -- If NULL passed for 'compare' function, will compare data pointer addresses
 * -- Returns NULL on failure
 * =============================================================================
 */
list_t*
TMlist_alloc (TM_ARGDECL  long (*compare)(const void*, const void*));


/* =============================================================================
 * list_free
 * =============================================================================
 */
void
list_free (list_t* listPtr);


/* =============================================================================
 * Plist_free
 * -- If NULL passed for 'compare' function, will compare data pointer addresses
 * -- Returns NULL on failure
 * =============================================================================
 */
void
Plist_free (list_t* listPtr);


/* =============================================================================
 * TMlist_free
 * -- If NULL passed for 'compare' function, will compare data pointer addresses
 * -- Returns NULL on failure
 * =============================================================================
 */
void
TMlist_free (TM_ARGDECL  list_t* listPtr);



/* =============================================================================
 * list_isEmpty
 * -- Return TRUE if list is empty, else FALSE
 * =============================================================================
 */
bool_t
list_isEmpty (list_t* listPtr);


/* =============================================================================
 * TMlist_isEmpty
 * -- Return TRUE if list is empty, else FALSE
 * =============================================================================
 */
TM_CALLABLE
bool_t
TMlist_isEmpty (TM_ARGDECL  list_t* listPtr);


/* =============================================================================
 * list_getSize
 * -- Returns size of list
 * =============================================================================
 */
long
list_getSize (list_t* listPtr);


/* =============================================================================
 * TMlist_getSize
 * -- Returns size of list
 * =============================================================================
 */
TM_CALLABLE
long
TMlist_getSize (TM_ARGDECL  list_t* listPtr);


/* =============================================================================
 * list_find
 * -- Returns NULL if not found, else returns pointer to data
 * =============================================================================
 */
void*
list_find (list_t* listPtr, void* dataPtr);


/* =============================================================================
 * TMlist_find
 * -- Returns NULL if not found, else returns pointer to data
 * =============================================================================
 */
TM_CALLABLE
void*
TMlist_find (TM_ARGDECL  list_t* listPtr, void* dataPtr);


/* =============================================================================
 * list_insert
 * -- Return TRUE on success, else FALSE
 * =============================================================================
 */
bool_t
list_insert (list_t* listPtr, void* dataPtr);


/* =============================================================================
 * Plist_insert
 * -- Return TRUE on success, else FALSE
 * =============================================================================
 */
bool_t
Plist_insert (list_t* listPtr, void* dataPtr);


/* =============================================================================
 * TMlist_insert
 * -- Return TRUE on success, else FALSE
 * =============================================================================
 */
TM_CALLABLE
bool_t
TMlist_insert (TM_ARGDECL  list_t* listPtr, void* dataPtr);


/* =============================================================================
 * list_remove
 * -- Returns TRUE if successful, else FALSE
 * =============================================================================
 */
bool_t
list_remove (list_t* listPtr, void* dataPtr);


/* =============================================================================
 * Plist_remove
 * -- Returns TRUE if successful, else FALSE
 * =============================================================================
 */
bool_t
Plist_remove (list_t* listPtr, void* dataPtr);


/* =============================================================================
 * TMlist_remove
 * -- Returns TRUE if successful, else FALSE
 * =============================================================================
 */
TM_CALLABLE
bool_t
TMlist_remove (TM_ARGDECL  list_t* listPtr, void* dataPtr);


/* =============================================================================
 * list_clear
 * -- Removes all elements
 * =============================================================================
 */
void
list_clear (list_t* listPtr);


/* =============================================================================
 * Plist_clear
 * -- Removes all elements
 * =============================================================================
 */
void
Plist_clear (list_t* listPtr);

}
